package com.winlator.winhandler;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

// import com.winlator.XServerDisplayActivity;
import com.winlator.core.StringUtils;
import com.winlator.inputcontrols.ControllerManager;
import com.winlator.inputcontrols.ControlsProfile;
import com.winlator.inputcontrols.ExternalController;
import com.winlator.inputcontrols.GamepadState;
import com.winlator.inputcontrols.TouchMouse;
import com.winlator.math.XForm;
import com.winlator.widget.InputControlsView;
import com.winlator.widget.XServerView;
import com.winlator.xserver.Pointer;
import com.winlator.xserver.XKeycode;
import com.winlator.xserver.XServer;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Array;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import com.winlator.PrefManager;
import kotlin.UShort;
import timber.log.Timber;

public class WinHandler {
    private static final short SERVER_PORT = 7947;
    private static final short CLIENT_PORT = 7946;
    private final ArrayDeque<Runnable> actions;
    private ExternalController currentController;
    private MappedByteBuffer gamepadBuffer;
    private int gyroTriggerButton;
    private byte dinputMapperType;
    private final List<Integer> gamepadClients;
    private boolean initReceived;
    private GamepadState lastVirtualState;
    private InetAddress localhost;
    private OnGetProcessInfoListener onGetProcessInfoListener;
    private PreferredInputApi preferredInputApi;
    private final ByteBuffer receiveData;
    private final DatagramPacket receivePacket;
    private boolean running;
    private final MappedByteBuffer[] extraGamepadBuffers = new MappedByteBuffer[3];
    private final ExternalController[] extraControllers = new ExternalController[3];
    private boolean xinputDisabled = false;
    private byte triggerType;
    private final ByteBuffer sendData;
    private final DatagramPacket sendPacket;
    private Thread rumblePollerThread;
    private DatagramSocket socket;
    private final ArrayList<Integer> xinputProcesses;
    private final XServer xServer;
    private final XServerView xServerView;

    private InputControlsView inputControlsView;
    private boolean xinputDisabledInitialized = false;
    private final Set<String> ignoredGroups = new HashSet();
    private final Set<Integer> ignoredDeviceIds = new HashSet();
    private boolean isShowingAssignDialog = false;
    private boolean gyroEnabled = false;
    private boolean isToggleMode = false;
    private boolean isGyroActive = false;
    private boolean processGyroWithLeftTrigger = false;
    private float gyroSensitivityX = 0.35f;
    private float gyroSensitivityY = 0.25f;
    private float smoothingFactor = 0.45f;
    private boolean invertGyroX = true;
    private boolean invertGyroY = false;
    private float gyroDeadzone = 0.01f;
    private float smoothGyroX = 0.0f;
    private float smoothGyroY = 0.0f;
    private float gyroX = 0.0f;
    private float gyroY = 0.0f;
    private float lastSentGX = 0.0f;
    private float lastSentGY = 0.0f;
    private volatile boolean hasVirtualState = false;
    private boolean gyroToLeftStick = false;
    private boolean lastVirtualActivatorPressed = false;
    private final short[] lastLow = new short[4];
    private final short[] lastHigh = new short[4];
    private final boolean[][] turboEnabled = (boolean[][]) Array.newInstance((Class<?>) Boolean.TYPE, 4, 15);
    private final boolean[] includeTriggers = new boolean[4];
    private final long[] lastTurboTick = new long[4];
    private volatile boolean turboPhaseOn = true;
    private volatile long turboLastFlipMs = 0;
    private volatile boolean anyTurboEnabled = false;
    private boolean virtualExclusiveP1 = true;
    private final ControllerManager controllerManager = ControllerManager.getInstance();

    // Add method to set InputControlsView
    public void setInputControlsView(InputControlsView view) {
        this.inputControlsView = view;
    }

    public enum PreferredInputApi {
        AUTO,
        DINPUT,
        XINPUT,
        BOTH
    }

    public WinHandler(XServer xServer, XServerView xServerView) {
        ByteBuffer allocate = ByteBuffer.allocate(64);
        ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;
        ByteBuffer order = allocate.order(byteOrder);
        this.sendData = order;
        ByteBuffer order2 = ByteBuffer.allocate(64).order(byteOrder);
        this.receiveData = order2;
        this.sendPacket = new DatagramPacket(order.array(), 64);
        this.receivePacket = new DatagramPacket(order2.array(), 64);
        this.actions = new ArrayDeque<>();
        this.initReceived = false;
        this.running = false;
        this.dinputMapperType = (byte) 1;
        this.preferredInputApi = PreferredInputApi.BOTH;
        this.gamepadClients = new CopyOnWriteArrayList();
        this.xinputProcesses = new ArrayList<>();
        this.xServer = xServer;
        this.xServerView = xServerView;
    }

    private boolean sendPacket(int port) {
        try {
            int size = this.sendData.position();
            if (size == 0) {
                return false;
            }
            this.sendPacket.setAddress(this.localhost);
            this.sendPacket.setPort(port);
            this.socket.send(this.sendPacket);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean sendPacket(int port, byte[] data) {
        try {
            DatagramPacket sendPacket = new DatagramPacket(data, data.length);
            sendPacket.setAddress(this.localhost);
            sendPacket.setPort(port);
            this.socket.send(sendPacket);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void exec(String command) {
        String command2 = command.trim();
        if (command2.isEmpty()) {
            return;
        }
        String[] cmdList = command2.split(" ", 2);
        final String filename = cmdList[0];
        final String parameters = cmdList.length > 1 ? cmdList[1] : "";
        addAction(() -> {
            byte[] filenameBytes = filename.getBytes();
            byte[] parametersBytes = parameters.getBytes();
            this.sendData.rewind();
            this.sendData.put(RequestCodes.EXEC);
            this.sendData.putInt(filenameBytes.length + parametersBytes.length + 8);
            this.sendData.putInt(filenameBytes.length);
            this.sendData.putInt(parametersBytes.length);
            this.sendData.put(filenameBytes);
            this.sendData.put(parametersBytes);
            sendPacket(CLIENT_PORT);
        });
    }

    public void killProcess(String processName) {
        killProcess(processName, 0);
    }

    public void killProcess(final String processName, final int pid) {
        addAction(() -> {
            this.sendData.rewind();
            this.sendData.put(RequestCodes.KILL_PROCESS);
            if (processName == null) {
                this.sendData.putInt(0);
            } else {
                byte[] bytes = processName.getBytes();
                int minLength = Math.min(bytes.length, 55);
                this.sendData.putInt(minLength);
                this.sendData.put(bytes, 0, minLength);
            }
            this.sendData.putInt(pid);
            sendPacket(CLIENT_PORT);
        });
    }

    public void listProcesses() {
        addAction(() -> {
            OnGetProcessInfoListener onGetProcessInfoListener;
            this.sendData.rewind();
            this.sendData.put(RequestCodes.LIST_PROCESSES);
            this.sendData.putInt(0);
            if (!sendPacket(CLIENT_PORT) && (onGetProcessInfoListener = this.onGetProcessInfoListener) != null) {
                onGetProcessInfoListener.onGetProcessInfo(0, 0, null);
            }
        });
    }

    public void setProcessAffinity(final String processName, final int affinityMask) {
        addAction(() -> {
            byte[] bytes = processName.getBytes();
            this.sendData.rewind();
            this.sendData.put(RequestCodes.SET_PROCESS_AFFINITY);
            this.sendData.putInt(bytes.length + 9);
            this.sendData.putInt(0);
            this.sendData.putInt(affinityMask);
            this.sendData.put((byte)bytes.length);
            this.sendData.put(bytes);
            sendPacket(CLIENT_PORT);
        });
    }

    public void setProcessAffinity(final int pid, final int affinityMask) {
        addAction(() -> {
            sendData.rewind();
            sendData.put(RequestCodes.SET_PROCESS_AFFINITY);
            sendData.putInt(9);
            sendData.putInt(pid);
            sendData.putInt(affinityMask);
            sendData.put((byte)0);
            sendPacket(CLIENT_PORT);
        });
    }

    public void mouseEvent(final int flags, final int dx, final int dy, final int wheelDelta) {
        if (this.initReceived) {
            addAction(() -> {
                this.sendData.rewind();
                this.sendData.put(RequestCodes.MOUSE_EVENT);
                this.sendData.putInt(10);
                this.sendData.putInt(flags);
                this.sendData.putShort((short) dx);
                this.sendData.putShort((short) dy);
                this.sendData.putShort((short) wheelDelta);
                this.sendData.put((byte) ((flags & MouseEventFlags.MOVE) != 0 ? 1 : 0)); // cursor pos feedback
                sendPacket(CLIENT_PORT);
            });
        }
    }

    public void keyboardEvent(byte vkey, int flags) {
        if (!initReceived) return;
        addAction(() -> {
            sendData.rewind();
            sendData.put(RequestCodes.KEYBOARD_EVENT);
            sendData.put(vkey);
            sendData.putInt(flags);
            sendPacket(CLIENT_PORT);
        });
    }

    public void bringToFront(String processName) {
        bringToFront(processName, 0L);
    }

    public void bringToFront(final String processName, final long handle) {
        addAction(() -> {
            this.sendData.rewind();
            this.sendData.put(RequestCodes.BRING_TO_FRONT);
            byte[] bytes = processName.getBytes();
            int minLength = Math.min(bytes.length, 51);
            this.sendData.putInt(minLength);
            this.sendData.put(bytes, 0, minLength);
            this.sendData.putLong(handle);
            sendPacket(CLIENT_PORT);
        });
    }

    public void setClipboardData(final String data) {
        addAction(() -> {
            this.sendData.rewind();
            byte[] bytes = data.getBytes();
            this.sendData.put((byte) 14);
            this.sendData.putInt(bytes.length);
            if (sendPacket(7946)) {
                sendPacket(7946, bytes);
            }
        });
    }

    private void addAction(Runnable action) {
        synchronized (this.actions) {
            this.actions.add(action);
            this.actions.notify();
        }
    }

    public OnGetProcessInfoListener getOnGetProcessInfoListener() {
        return onGetProcessInfoListener;
    }

    public void setOnGetProcessInfoListener(OnGetProcessInfoListener onGetProcessInfoListener) {
        synchronized (this.actions) {
            this.onGetProcessInfoListener = onGetProcessInfoListener;
        }
    }

    private void loadTurboPrefsForAllSlots() {
        boolean any = false;
        this.anyTurboEnabled = any;
    }

    private static boolean isSystemNavKey(int code) {
        switch (code) {
            case 3:
            case 4:
            case 24:
            case 25:
            case 82:
            case 85:
            case 87:
            case 88:
            case 164:
            case 187:
                return true;
            default:
                return false;
        }
    }

    private void startSendThread() {
        Executors.newSingleThreadExecutor().execute(() -> {
            while (this.running) {
                synchronized (this.actions) {
                    while (this.initReceived && !this.actions.isEmpty()) {
                        this.actions.poll().run();
                    }
                    try {
                        this.actions.wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
        });
    }

    public void stop() {
        this.running = false;
        DatagramSocket datagramSocket = this.socket;
        if (datagramSocket != null) {
            datagramSocket.close();
            this.socket = null;
        }
        synchronized (this.actions) {
            this.actions.notify();
        }
    }

    private void handleRequest(byte requestCode, final int port) throws IOException {
        Log.d("WinHandler", "handleRequest");
        boolean enabled = true;
        ExternalController externalController;
        switch (requestCode) {
            case RequestCodes.INIT:
                this.initReceived = true;
                this.triggerType = (byte) PrefManager.getInt("trigger_type", 1);
                this.virtualExclusiveP1 = PrefManager.getBoolean("virtual_exclusive_p1", true);
                refreshControllerMappings();
                synchronized (this.actions) {
                    this.actions.notify();
                }
                return;
            case RequestCodes.GET_PROCESS:
                if (this.onGetProcessInfoListener == null) {
                    return;
                }
                ByteBuffer byteBuffer = this.receiveData;
                byteBuffer.position(byteBuffer.position() + 4);
                int numProcesses = this.receiveData.getShort();
                int index = this.receiveData.getShort();
                int pid = this.receiveData.getInt();
                long memoryUsage = this.receiveData.getLong();
                int affinityMask = this.receiveData.getInt();
                boolean wow64Process = this.receiveData.get() == 1;
                byte[] bytes = new byte[32];
                this.receiveData.get(bytes);
                String name = StringUtils.fromANSIString(bytes);
                this.onGetProcessInfoListener.onGetProcessInfo(index, numProcesses, new ProcessInfo(pid, name, memoryUsage, affinityMask, wow64Process));
                return;
            case RequestCodes.GET_GAMEPAD:
                if (this.xinputDisabled) {
                    return;
                }
                boolean isXInput = this.receiveData.get() == 1;
                boolean notify = this.receiveData.get() == 1;
                final ControlsProfile profile = inputControlsView.getProfile();
                final boolean useVirtualGamepad = inputControlsView != null && profile != null && profile.isVirtualGamepad();
                int processId = this.receiveData.getInt();
                if (!useVirtualGamepad && ((externalController = this.currentController) == null || !externalController.isConnected())) {
                    this.currentController = ExternalController.getController(0);
                    if (this.currentController != null) {
                        this.currentController.setTriggerType(this.triggerType);
                    }
                }
                boolean enabled2 = this.currentController != null || useVirtualGamepad;
                if (enabled2) {
                    switch (this.preferredInputApi) {
                        case DINPUT:
                            boolean hasXInputProcess = this.xinputProcesses.contains(Integer.valueOf(processId));
                            if (isXInput) {
                                if (!hasXInputProcess) {
                                    this.xinputProcesses.add(Integer.valueOf(processId));
                                    break;
                                }
                            } else if (hasXInputProcess) {
                                enabled = false;
                                break;
                            }
                            break;
                        case XINPUT:
                            if (isXInput) {
                                enabled = false;
                                break;
                            }
                            break;
                        case BOTH:
                            if (!isXInput) {
                                enabled = false;
                                break;
                            }
                            break;
                    }
                    if (notify) {
                        if (!this.gamepadClients.contains(Integer.valueOf(port))) {
                            this.gamepadClients.add(Integer.valueOf(port));
                        }
                    } else {
                        this.gamepadClients.remove(Integer.valueOf(port));
                    }
                    final boolean finalEnabled = enabled;
                    addAction(() -> {
                        this.sendData.rewind();
                        this.sendData.put((byte) 8);
                        if (finalEnabled) {
                            this.sendData.putInt(!useVirtualGamepad ? this.currentController.getDeviceId() : profile.id);
                            this.sendData.put(this.dinputMapperType);
                            byte[] bytes2 = (useVirtualGamepad ? profile.getName() : this.currentController.getName()).getBytes();
                            this.sendData.putInt(bytes2.length);
                            this.sendData.put(bytes2);
                        } else {
                            this.sendData.putInt(0);
                            this.sendData.put((byte) 0);
                            this.sendData.putInt(0);
                        }
                        sendPacket(port);
                    });
                    return;
                }
                enabled = enabled2;
                if (!enabled) {
                }
                this.gamepadClients.remove(Integer.valueOf(port));
                final boolean finalEnabled2 = enabled;
                addAction(() -> {
                    this.sendData.rewind();
                    this.sendData.put((byte) 8);
                    if (finalEnabled2) {
                        this.sendData.putInt(!useVirtualGamepad ? this.currentController.getDeviceId() : profile.id);
                        this.sendData.put(this.dinputMapperType);
                        byte[] bytes2 = (useVirtualGamepad ? profile.getName() : this.currentController.getName()).getBytes();
                        this.sendData.putInt(bytes2.length);
                        this.sendData.put(bytes2);
                    } else {
                        this.sendData.putInt(0);
                        this.sendData.put((byte) 0);
                        this.sendData.putInt(0);
                    }
                    sendPacket(port);
                });
                return;
            case RequestCodes.GET_GAMEPAD_STATE:
                if (this.xinputDisabled) {
                    return;
                }
                final int gamepadId = this.receiveData.getInt();
                final ControlsProfile profile2 = inputControlsView.getProfile();
                final boolean useVirtualGamepad2 = inputControlsView != null && profile2 != null && profile2.isVirtualGamepad();
                ExternalController externalController2 = this.currentController;
                final boolean enabled3 = externalController2 != null || useVirtualGamepad2;
                if (externalController2 != null && externalController2.getDeviceId() != gamepadId) {
                    this.currentController = null;
                }
                addAction(() -> {
                    sendData.rewind();
                    sendData.put(RequestCodes.GET_GAMEPAD_STATE);
                    this.sendData.put((byte)(enabled3 ? 1 : 0));
                    if (enabled3) {
                        this.sendData.putInt(gamepadId);
                        if (useVirtualGamepad2) {
                            inputControlsView.getProfile().getGamepadState().writeTo(this.sendData);
                        } else {
                            this.currentController.state.writeTo(this.sendData);
                        }
                    }
                    sendPacket(port);
                });
                return;
            case RequestCodes.RELEASE_GAMEPAD:
                this.currentController = null;
                this.gamepadClients.clear();
                this.xinputProcesses.clear();
                return;
            case RequestCodes.CURSOR_POS_FEEDBACK:
                short x = this.receiveData.getShort();
                short y = this.receiveData.getShort();
                xServer.pointer.setX(x);
                xServer.pointer.setY(y);
                xServerView.requestRender();
                return;
            default:
                return;
        }
    }

    public void start() {
        try {
            this.localhost = InetAddress.getLocalHost();
            File p1 = new File("/data/data/app.gamenative/files/imagefs/tmp/gamepad.mem");
            p1.getParentFile().mkdirs();
            RandomAccessFile raf = new RandomAccessFile(p1, "rw");
            Log.d("WinHandler", "Memory file exists: " + p1.exists() +
            ", size: " + p1.length() + ", readable: " + p1.canRead() +
            ", writable: " + p1.canWrite());
            try {
                raf.setLength(64L);
                this.gamepadBuffer = raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0L, 64L);
                this.gamepadBuffer.order(ByteOrder.LITTLE_ENDIAN);
                Log.i("WinHandler", "Mapped SHM for Player 1");
                raf.close();
                for (int i = 0; i < this.extraGamepadBuffers.length; i++) {
                    String path = "/data/data/app.gamenative/files/imagefs/tmp/gamepad" + (i + 1) + ".mem";
                    File f = new File(path);
                    raf = new RandomAccessFile(f, "rw");
                    try {
                        raf.setLength(64L);
                        this.extraGamepadBuffers[i] = raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0L, 64L);
                        this.extraGamepadBuffers[i].order(ByteOrder.LITTLE_ENDIAN);
                        Log.i("WinHandler", "Mapped SHM for Player " + (i + 2));
                        raf.close();
                    } finally {
                    }
                }
            } finally {
                try {
                    raf.close();
                } catch (Throwable th) {
                    th.addSuppressed(th);
                }
            }
        } catch (IOException e) {
            Log.e("WinHandler", "FATAL: Failed to create memory-mapped file(s).", e);
        }
        this.running = true;
        startSendThread();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                DatagramSocket datagramSocket = new DatagramSocket((SocketAddress) null);
                this.socket = datagramSocket;
                datagramSocket.setReuseAddress(true);
                this.socket.bind(new InetSocketAddress((InetAddress) null, 7947));
                while (this.running) {
                    this.socket.receive(this.receivePacket);
                    synchronized (this.actions) {
                        this.receiveData.rewind();
                        byte requestCode = this.receiveData.get();
                        handleRequest(requestCode, this.receivePacket.getPort());
                    }
                }
            } catch (IOException e) {
            }
        });
//        startRumblePoller();
    }

    public void sendGamepadState() {
        Log.d("WinHandler", "send gamepad state");
        if (!this.initReceived || this.gamepadClients.isEmpty()) {
            return;
        }
        final ControlsProfile profile = inputControlsView.getProfile();
        final boolean useVirtualGamepad = profile != null && profile.isVirtualGamepad();
        final boolean enabled = this.currentController != null || useVirtualGamepad;
        Iterator<Integer> it = this.gamepadClients.iterator();
        while (it.hasNext()) {
            final int port = it.next().intValue();
            addAction(() -> {
                this.sendData.rewind();
                sendData.put(RequestCodes.GET_GAMEPAD_STATE);
                sendData.put((byte)(enabled ? 1 : 0));
                if (enabled) {
                    this.sendData.putInt(!useVirtualGamepad ? this.currentController.getDeviceId() : inputControlsView.getProfile().id);
                    if (useVirtualGamepad) {
                        inputControlsView.getProfile().getGamepadState().writeTo(sendData);
                    } else {
                        this.currentController.state.writeTo(this.sendData);
                    }
                }
                sendPacket(port);
            });
        }
    }

    public void pokeSharedMemory() {
        Log.d("WinHandler", "pokeSharedMemory");
        if (this.gamepadBuffer == null) {
            return;
        }
        ControlsProfile profile = inputControlsView.getProfile();
        boolean useVirtual = profile != null && profile.isVirtualGamepad();
        if (useVirtual) {
            GamepadState s = profile.getGamepadState();
            if (s != null) {
                this.lastVirtualState = s;
                this.hasVirtualState = true;
                writeStateToMappedBuffer(s, this.gamepadBuffer, true, 0);
                return;
            }
            return;
        }
        ensureP1Controller();
        if (this.currentController != null) {
            writeStateToMappedBuffer(this.currentController.state, this.gamepadBuffer, true, 0);
        } else if (this.hasVirtualState && this.lastVirtualState != null) {
            writeStateToMappedBuffer(this.lastVirtualState, this.gamepadBuffer, true, 0);
        }
    }

    private void writeStateToMappedBuffer(GamepadState gamepadState, MappedByteBuffer mappedByteBuffer, boolean z, int i) {
        if (mappedByteBuffer == null || gamepadState == null) {
            Log.w("WinHandler", "Cannot write to buffer - buffer or state is null");
            return;
        }

        Log.d("WinHandler", "Writing gamepad state: LX=" + gamepadState.thumbLX +
          ", LY=" + gamepadState.thumbLY + ", buttons=" + gamepadState.buttons);
        mappedByteBuffer.clear();
        float fClamp = gamepadState.thumbLX;
        float fClamp2 = gamepadState.thumbLY;
        float fClamp3 = gamepadState.thumbRX;
        float fClamp4 = gamepadState.thumbRY;
        mappedByteBuffer.putShort((short) (fClamp * 32767.0f));
        mappedByteBuffer.putShort((short) (fClamp2 * 32767.0f));
        mappedByteBuffer.putShort((short) (fClamp3 * 32767.0f));
        mappedByteBuffer.putShort((short) (32767.0f * fClamp4));
        float fMax = Math.max(0.0f, Math.min(1.0f, gamepadState.triggerL));
        float fMax2 = Math.max(0.0f, Math.min(1.0f, gamepadState.triggerR));
        byte[] bArr = {
                (byte) (gamepadState.isPressed(0) ? 1 : 0),
                (byte) (gamepadState.isPressed(1) ? 1 : 0),
                (byte) (gamepadState.isPressed(2) ? 1 : 0),
                (byte) (gamepadState.isPressed(3) ? 1 : 0),
                (byte) (gamepadState.isPressed(6) ? 1 : 0),
                0,
                (byte) (gamepadState.isPressed(7) ? 1 : 0),
                (byte) (gamepadState.isPressed(8) ? 1 : 0),
                (byte) (gamepadState.isPressed(9) ? 1 : 0),
                (byte) (gamepadState.isPressed(4) ? 1 : 0),
                (byte) (gamepadState.isPressed(5) ? 1 : 0),
                gamepadState.dpad[0] ? (byte) 1 : (byte) 0,
                gamepadState.dpad[2] ? (byte) 1 : (byte) 0,
                gamepadState.dpad[3] ? (byte) 1 : (byte) 0,
                gamepadState.dpad[1] ? (byte) 1 : (byte) 0};
        applyTurboMask(i, bArr);
        if (!this.turboPhaseOn && i >= 0 && i < this.includeTriggers.length && this.includeTriggers[i]) {
            fMax = 0.0f;
            fMax2 = 0.0f;
        }
        float fSqrt = (float) Math.sqrt(fMax);
        float fSqrt2 = (float) Math.sqrt(fMax2);
        int iRound = Math.round(fSqrt * 65534.0f) - 32767;
        int iRound2 = Math.round(65534.0f * fSqrt2) - 32767;
        mappedByteBuffer.putShort((short) iRound);
        mappedByteBuffer.putShort((short) iRound2);
        mappedByteBuffer.put(bArr);
        mappedByteBuffer.put((byte) 0);

        Log.d("WinHandler", "Successfully wrote to memory buffer");
    }

    public void refreshControllerMappings() {
        Log.d("WinHandler", "Refreshing controller assignments from settings...");
        this.currentController = null;
        for (int i = 0; i < this.extraControllers.length; i++) {
            this.extraControllers[i] = null;
        }
        this.controllerManager.scanForDevices();

        // Add debug logging
        Log.d("WinHandler", "Detected devices: " + this.controllerManager.getDetectedDevices().size());
        for (InputDevice device : this.controllerManager.getDetectedDevices()) {
            Log.d("WinHandler", "  Device: " + device.getName() + " (ID: " + device.getId() + ")");
        }

        if (this.virtualExclusiveP1 && isVirtualActive()) {
            this.currentController = null;
        } else {
            InputDevice p1Device = this.controllerManager.getAssignedDeviceForSlot(0);
            Log.d("WinHandler", "Device assigned to slot 0: " + (p1Device != null ? p1Device.getName() : "null"));

            // Auto-assign first detected controller to Player 1 if none assigned
            if (p1Device == null && !this.controllerManager.getDetectedDevices().isEmpty()) {
                InputDevice firstController = this.controllerManager.getDetectedDevices().get(0);
                Log.i("WinHandler", "Auto-assigning first controller to Player 1: " + firstController.getName());
                this.controllerManager.assignDeviceToSlot(0, firstController);
                p1Device = firstController;
            }

            if (p1Device != null) {
                this.currentController = ExternalController.getController(p1Device.getId());
                if (this.currentController != null) {
                    this.currentController.setTriggerType(this.triggerType);
                    Log.i("WinHandler", "Initialized Player 1 with: " + p1Device.getName());
                } else {
                    Log.w("WinHandler", "Failed to create ExternalController for device: " + p1Device.getName());
                }
            } else {
                Log.w("WinHandler", "No device assigned to Player 1 slot");
            }
        }
        for (int i2 = 0; i2 < this.extraControllers.length; i2++) {
            InputDevice dev = this.controllerManager.getAssignedDeviceForSlot(i2 + 1);
            if (dev != null) {
                this.extraControllers[i2] = ExternalController.getController(dev.getId());
                Log.i("WinHandler", "Initialized Player " + (i2 + 2) + " with: " + dev.getName());
            }
        }
        loadTurboPrefsForAllSlots();
    }

    public void ensureP1Controller() {
        Log.d("WinHandler", "ensureP1Controller");
        ControlsProfile profile = inputControlsView.getProfile();
        if (profile != null && profile.isVirtualGamepad()) {
            Log.d("WinHandler", "Using virtual gamepad");
            this.currentController = null;
            return;
        }
        if (this.currentController != null) {
            Log.d("WinHandler", "Controller already exists: " + this.currentController.getName());
            return;
        }
        if (this.currentController == null) {
            Log.d("WinHandler", "Got controller: " + this.currentController.getName());
            this.currentController = ExternalController.getController(0);
        }
    }

    private void applyTurboMask(int slot, byte[] sdlButtons) {
        if (slot < 0 || slot >= 4 || this.turboPhaseOn) {
            return;
        }
        boolean[] mask = this.turboEnabled[slot];
        for (int i = 0; i < 15 && i < sdlButtons.length; i++) {
            if (mask[i] && sdlButtons[i] != 0) {
                sdlButtons[i] = 0;
            }
        }
    }

    public boolean onGenericMotionEvent(MotionEvent event) {
        Log.d("WinHandler", "Java received motion event - device: " + event.getDeviceId() +
          ", source: " + event.getSource() + ", action: " + event.getAction());
        boolean handled = false;
        ExternalController externalController = this.currentController;
        Log.d("WinHandler", "externalController = " + externalController);
//        Log.d("WinHandler", "externalController.getDeviceId() == event.getDeviceId()? = " + (externalController.getDeviceId() == event.getDeviceId()));
//        Log.d("WinHandler", "this.currentController.updateStateFromMotionEvent(event) ? " + (this.currentController.updateStateFromMotionEvent(event)));
        if (externalController != null && externalController.getDeviceId() == event.getDeviceId() && (handled = this.currentController.updateStateFromMotionEvent(event))) {
            if (handled) {
                Log.d("WinHandler", "Writing to shared memory after motion event");
                sendGamepadState();
                writeStateToMappedBuffer(this.currentController.state, this.gamepadBuffer, true, 0);
            }
            else {
                Log.d("WinHandler", "Motion event not handled");
            }
        }
        else {
            Log.d("WinHandler", "no controller, ignoring motion event");
        }
        return handled;
    }

    public boolean onKeyEvent(KeyEvent event) {
        Log.d("WinHandler", "Java received key event - device: " + event.getDeviceId() +
                ", keyCode: " + event.getKeyCode() + ", action: " + event.getAction());
        boolean handled = false;
        ExternalController externalController = this.currentController;
        Log.d("WinHandler", "externalController = " + externalController);
//        Log.d("WinHandler", "externalController.getDeviceId() == event.getDeviceId()? = " + (externalController.getDeviceId() == event.getDeviceId()));
//        Log.d("WinHandler", "event.getRepeatCount() == 0 ? " + (event.getRepeatCount() == 0));
        if (externalController != null && externalController.getDeviceId() == event.getDeviceId() && event.getRepeatCount() == 0) {
            int action = event.getAction();
            if (action == KeyEvent.ACTION_DOWN) {
                handled = this.currentController.updateStateFromKeyEvent(event);
            } else if (action == KeyEvent.ACTION_UP) {
                handled = this.currentController.updateStateFromKeyEvent(event);
            }
            if (handled) {
                sendGamepadState();
                writeStateToMappedBuffer(this.currentController.state, this.gamepadBuffer, true, 0);
            }
            else {
                Log.d("WinHandler", "Key event not handled");
            }
        }
        else {
            Log.d("WinHandler", "no controller, ignoring key event");
        }
        return handled;
    }

    public void setDInputMapperType(byte dinputMapperType) {
        this.dinputMapperType = dinputMapperType;
    }

    public void setPreferredInputApi(PreferredInputApi preferredInputApi) {
        this.preferredInputApi = preferredInputApi;
    }

    public ExternalController getCurrentController() {
        return this.currentController;
    }

    private boolean isVirtualActive() {
        ControlsProfile p = inputControlsView.getProfile();
        return p != null && p.isVirtualGamepad();
    }
}
