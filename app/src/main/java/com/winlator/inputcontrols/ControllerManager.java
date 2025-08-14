package com.winlator.inputcontrols;

import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Build;
import android.util.SparseArray;
import android.view.InputDevice;
import androidx.core.view.InputDeviceCompat;
import com.winlator.PrefManager;
import java.util.ArrayList;
import java.util.List;

/* loaded from: classes14.dex */
public class ControllerManager {
    public static final String PREF_ENABLED_SLOTS_PREFIX = "enabled_slot_";
    public static final String PREF_PLAYER_SLOT_PREFIX = "controller_slot_";
    public static final String PREF_VIBRATE_SLOT_PREFIX = "vibrate_slot_";
    private static ControllerManager instance;
    private Context context;
    private InputManager inputManager;
    private final List<InputDevice> detectedDevices = new ArrayList();
    private final SparseArray<String> slotAssignments = new SparseArray<>();
    private final boolean[] enabledSlots = new boolean[4];
    private final boolean[] vibrationEnabled = {true, true, true, true};

    public static synchronized ControllerManager getInstance() {
        if (instance == null) {
            instance = new ControllerManager();
        }
        return instance;
    }

    private ControllerManager() {
    }

    public void init(Context context) {
        this.context = context.getApplicationContext();
        PrefManager.init(context);
        this.inputManager = (InputManager) this.context.getSystemService("input");
        loadAssignments();
        scanForDevices();
    }

    public void scanForDevices() {
        this.detectedDevices.clear();
        int[] deviceIds = this.inputManager.getInputDeviceIds();
        for (int deviceId : deviceIds) {
            InputDevice device = this.inputManager.getInputDevice(deviceId);
            if (device != null && !device.isVirtual() && isGameController(device)) {
                this.detectedDevices.add(device);
            }
        }
    }

    private void loadAssignments() {
        this.slotAssignments.clear();
        int i = 0;
        while (i < 4) {
            String prefKey = PREF_PLAYER_SLOT_PREFIX + i;
            String deviceIdentifier = PrefManager.getString(prefKey, "");
            if (deviceIdentifier != null && !deviceIdentifier.isEmpty()) {
                this.slotAssignments.put(i, deviceIdentifier);
            }
            String enabledKey = PREF_ENABLED_SLOTS_PREFIX + i;
            boolean z = false;
            this.enabledSlots[i] = PrefManager.getBoolean(enabledKey, i == 0);
            String vibKey = PREF_VIBRATE_SLOT_PREFIX + i;
            boolean[] zArr = this.vibrationEnabled;
            if (i == 0) {
                z = true;
            }
            zArr[i] = PrefManager.getBoolean(vibKey, z);
            i++;
        }
    }

    public void saveAssignments() {
        for (int i = 0; i < 4; i++) {
            String deviceIdentifier = this.slotAssignments.get(i);
            String prefKey = PREF_PLAYER_SLOT_PREFIX + i;
            if (deviceIdentifier != null) {
                PrefManager.putString(prefKey, deviceIdentifier);
            } else {
                PrefManager.putString(prefKey, "");  // Clear the preference
            }
            String enabledKey = PREF_ENABLED_SLOTS_PREFIX + i;
            PrefManager.putBoolean(enabledKey, this.enabledSlots[i]);
            String vibKey = PREF_VIBRATE_SLOT_PREFIX + i;
            PrefManager.putBoolean(vibKey, this.vibrationEnabled[i]);
        }
    }

    private static class Caps {
        boolean anyButtons;
        boolean anyJoyAxis;
        boolean hat;
        boolean lxy;
        boolean rstick;
        boolean trigger;

        private Caps() {
        }
    }

    private static Caps analyzeCaps(InputDevice d) {
        Caps c = new Caps();
        if (d == null) {
            return c;
        }
        for (InputDevice.MotionRange r : d.getMotionRanges()) {
            int src = r.getSource();
            if ((16778257 & src) != 0) {
                c.anyJoyAxis = true;
                switch (r.getAxis()) {
                    case 0:
                    case 1:
                        c.lxy = true;
                        break;
                    case 12:
                    case 13:
                    case 14:
                        c.rstick = true;
                        break;
                    case 15:
                    case 16:
                        c.hat = true;
                        break;
                    case 17:
                    case 18:
                    case 22:
                    case 23:
                        c.trigger = true;
                        break;
                }
            }
        }
        int[] keys = {96, 97, 99, 100, 102, 103, 106, 107, 19, 20, 21, 22, 108, 109};
        boolean[] present = d.hasKeys(keys);
        int length = present.length;
        int i = 0;
        while (i < length) {
            boolean p = present[i];
            if (p) {
                c.anyButtons = true;
                break;
            }
            i++;
        }
        return c;
    }

    private static boolean isPointerLike(InputDevice device) {
        if (device == null) {
            return false;
        }
        int s = device.getSources();
        return (s & 8194) == 8194 || (s & 131076) == 131076 || (s & InputDeviceCompat.SOURCE_TOUCHPAD) == 1048584 || (s & InputDeviceCompat.SOURCE_STYLUS) == 16386 || (s & 2) == 2;
    }

    public static boolean isGameController(InputDevice d) {
        if (d == null) {
            return false;
        }
        int s = d.getSources();
        boolean hasControllerBits = ((16777232 & s) == 0 && (s & InputDeviceCompat.SOURCE_GAMEPAD) == 0) ? false : true;
        boolean pointer = isPointerLike(d);
        Caps c = analyzeCaps(d);
        if (pointer) {
            return c.hat || c.trigger || c.rstick;
        }
        if (hasControllerBits) {
            return c.lxy || c.hat || c.trigger || c.rstick || c.anyButtons;
        }
        return false;
    }

    public static String getDeviceIdentifier(InputDevice device) {
        if (device == null) {
            return null;
        }
        if (Build.VERSION.SDK_INT >= 29) {
            return device.getDescriptor();
        }
        return "vendor_" + device.getVendorId() + "_product_" + device.getProductId();
    }

    public List<InputDevice> getDetectedDevices() {
        return this.detectedDevices;
    }

    public int getEnabledPlayerCount() {
        int count = 0;
        for (boolean enabled : this.enabledSlots) {
            if (enabled) {
                count++;
            }
        }
        return count;
    }

    public void assignDeviceToSlot(int slotIndex, InputDevice device) {
        String newDeviceIdentifier;
        if (slotIndex < 0 || slotIndex >= 4 || (newDeviceIdentifier = getDeviceIdentifier(device)) == null) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            if (newDeviceIdentifier.equals(this.slotAssignments.get(i))) {
                this.slotAssignments.remove(i);
            }
        }
        this.slotAssignments.put(slotIndex, newDeviceIdentifier);
        saveAssignments();
    }

    public boolean hasEnabledUnassignedSlot() {
        for (int i = 0; i < 4; i++) {
            if (this.enabledSlots[i] && getAssignedDeviceForSlot(i) == null) {
                return true;
            }
        }
        return false;
    }

    public void unassignSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= 4) {
            return;
        }
        this.slotAssignments.remove(slotIndex);
        saveAssignments();
    }

    public int getSlotForDevice(int deviceId) {
        InputDevice device = this.inputManager.getInputDevice(deviceId);
        String deviceIdentifier = getDeviceIdentifier(device);
        if (deviceIdentifier == null) {
            return -1;
        }
        for (int i = 0; i < this.slotAssignments.size(); i++) {
            int key = this.slotAssignments.keyAt(i);
            String value = this.slotAssignments.valueAt(i);
            if (deviceIdentifier.equals(value)) {
                return key;
            }
        }
        return -1;
    }

    public InputDevice getAssignedDeviceForSlot(int slotIndex) {
        String assignedIdentifier = this.slotAssignments.get(slotIndex);
        if (assignedIdentifier == null) {
            return null;
        }
        for (InputDevice device : this.detectedDevices) {
            if (assignedIdentifier.equals(getDeviceIdentifier(device))) {
                return device;
            }
        }
        return null;
    }

    public void setSlotEnabled(int slotIndex, boolean isEnabled) {
        if (slotIndex < 0 || slotIndex >= 4) {
            return;
        }
        this.enabledSlots[slotIndex] = isEnabled;
        saveAssignments();
    }

    public boolean isSlotEnabled(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= 4) {
            return false;
        }
        return this.enabledSlots[slotIndex];
    }

    private static String sanitizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("(?i)(\\s*-?\\s*(touch|touchpad|sensor|motion).*)$", "").trim();
    }

    public static String makePhysicalGroupKey(InputDevice d) {
        if (d == null) {
            return null;
        }
        return d.getVendorId() + ":" + d.getProductId() + ":" + sanitizeName(d.getName());
    }

    public int getSlotForDeviceOrSibling(int deviceId) {
        InputDevice d = this.inputManager.getInputDevice(deviceId);
        if (d == null) {
            return -1;
        }
        int slot = getSlotForDevice(deviceId);
        if (slot != -1) {
            return slot;
        }
        String g = makePhysicalGroupKey(d);
        for (int i = 0; i < 4; i++) {
            InputDevice assigned = getAssignedDeviceForSlot(i);
            if (assigned != null && g.equals(makePhysicalGroupKey(assigned))) {
                return i;
            }
        }
        return -1;
    }

    public boolean isVibrationEnabled(int slot) {
        return slot >= 0 && slot < 4 && this.vibrationEnabled[slot];
    }

    public void setVibrationEnabled(int slot, boolean enabled) {
        if (slot < 0 || slot >= 4) {
            return;
        }
        this.vibrationEnabled[slot] = enabled;
        saveAssignments();
    }
}
