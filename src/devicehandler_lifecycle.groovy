// Standard Hooks
def installed() {
    log.trace "installed()"
    initialize()
}

def updated() {
    log.trace "updated()"
    sendEvent(name: "checkInterval", value: 12 * 60, displayed: false, data: [protocol: "cloud", scheme: "untracked"])
    refresh()
}

def initialize() {
    log.trace "initialize()"

    // Setup the ping
    sendEvent(name: "checkInterval", value: 12 * 60, displayed: false, data: [protocol: "cloud", scheme: "untracked"])
}
