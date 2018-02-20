metadata {
    // Automatically generated. Make future change here.
    definition(name: "Kevo Unofficial", namespace: "kennelbound-smartthings/kevolock", author: "Kennelbound") {
        capability "Actuator"
        capability "Sensor"
        capability "Health Check"
        capability "Lock"
        capability "Refresh"

        attribute 'firmware', 'string'
    }

    preferences {
        input name: 'kevoUsername', type: 'email', title: 'Kevo Email', description: 'Username to log into mykevo.com', required: true, displayDuringSetup: true
        input name: 'kevoPassword', type: 'password', title: 'Kevo Password', description: 'Password to log into mykevo.com', required: true, displayDuringSetup: true
        input name: 'kevoLockId', type: 'text', title: 'Lock ID', description: 'Can be found on mykevo.com in lock settings', required: true, displayDuringSetup: true
        input name: 'enableDebugLogging', type: 'boolean', title: 'Enable Debug Logging', description: 'Show Debug Logging in the Live Logs?', required: false, displayDuringSetup: false
    }

    tiles(scale: 2) {
        multiAttributeTile(name: "toggle", type: "generic", width: 6, height: 4) {
            tileAttribute("device.lock", key: "PRIMARY_CONTROL") {
                attributeState "locked", label: 'Locked', action: "lock.unlock", icon: "st.locks.lock.locked", backgroundColor: "#00A0DC", nextState: "unlocking"
                attributeState "locking", label: 'Locking', icon: "st.locks.lock.locked", backgroundColor: "#00A0DC", nextState: "locked"
                attributeState "lockedJammed", label: 'Jammed Locked', action: "lock.unlock", icon: "st.locks.lock.locked", backgroundColor: "#eded84", nextState: "unlocking"
                attributeState "unlocked", label: 'Unlocked', action: "lock.lock", icon: "st.locks.lock.unlocked", backgroundColor: "#FFFFFF", nextState: "locking"
                attributeState "unlocking", label: 'Unlocking', icon: "st.locks.lock.unlocked", backgroundColor: "#FFFFFF", nextState: "unlocked"
                attributeState "unlockedJammed", label: 'Jammed Unlocked', action: "lock.lock", icon: "st.locks.lock.unlocked", backgroundColor: "#FF0000", nextState: "locking"
                attributeState "unknown", label: 'Jammed', action: "lock.unlock", icon: "st.locks.lock.unlocked", backgroundColor: "#e86d13", nextState: "unlocking"
                attributeState "refreshing", label: 'Checking', icon: "st.secondary.refresh", backgroundColor: "#90d2a7"
            }
        }

        standardTile("lock", "device.lock", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label: 'lock', action: "lock.lock", icon: "st.locks.lock.locked"
        }
        standardTile("unlock", "device.lock", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label: 'unlock', action: "lock.unlock", icon: "st.locks.lock.unlocked"
        }
        standardTile("refresh", "device.lock", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label: '', action: "refresh.refresh", icon: "st.secondary.refresh"
        }

        valueTile("firmware", "device.firmware", width: 2, height: 2) {
            state "val", label: '${currentValue}', defaultState: 'Unknown'
        }

        main "toggle"
        details(["toggle", "lock", "unlock", "refresh"])//, "firmware"])
    }
}