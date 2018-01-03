include 'asynchttp_v1'

def noCallback(response, data) {
    log.error "Couldn't find response for callback ${data.get('callback')}"
}

def setLockState(state) {
    sendEvent name: 'lock', value: state
}

def unlock() {
    log.trace "unlock"
    lockState = 'logging_in_token'
    state.cookie = ""
    state.token = null
    kevoCommandGet '/login', null, "unlockLogin", "text/html"
}

def unlockLogin(response, data) {
    log.trace "unlockLogin:$response.status"
    lockState = 'logging_in_credentials'
    kevoCommandPost('/signin', [
            "user[username]"    : kevoUsername,
            "user[password]"    : kevoPassword,
            "authenticity_token": state.token,
            "commit"            : "LOGIN",
            "utf8"              : "✓"
    ], 'unlockCredentials', 'text/html')
}

def unlockCredentials(response, data) {
    log.trace "unlockCredentials:$response.status"
    if (response.status == 302 || response.status == 200) {
        log.trace "redirect or 200 from login post, considering successful"
    }
    lockState = "logging_in_verifying"
    kevoCommandGet('/user/locks', null, 'unlockSendCommand', 'text/html')
}

def unlockSendCommand(response, data) {
    log.trace "unlockSendCommand:$response.status"
    updateTokenAndCookie(response)

    lockState = 'unlocking'
    kevoCommandGet("/user/remote_locks/command/remote_unlock.json", ['arguments': kevoLockId], "unlockVerifyCommand", "text/json")
}

def unlockVerifyCommand(response, data) {
    log.trace "unlockVerifyCommand:$response.status"
    if (response.status == 200) {
        refresh()
    } else {
        log.error "couldn't determine lock status after unlocking, $e"
        lockState = 'unknown'
    }
}

def lock() {
    log.trace "lock"
    lockState = 'logging_in_token'
    state.cookie = ""
    state.token = null
    kevoCommandGet '/login', null, "lockLogin", "text/html"
}

def lockLogin(response, data) {
    log.trace "lockLogin:$response.status"
    lockState = 'logging_in_credentials'
    kevoCommandPost('/signin', [
            "user[username]"    : kevoUsername,
            "user[password]"    : kevoPassword,
            "authenticity_token": state.token,
            "commit"            : "LOGIN",
            "utf8"              : "✓"
    ], 'lockCredentials', 'text/html')
}

def lockCredentials(response, data) {
    log.trace "lockCredentials:$response.status"
    if (response.status == 302 || response.status == 200) {
        log.trace "redirect or 200 from login post, considering successful"
    }
    lockState = "logging_in_verifying"
    kevoCommandGet('/user/locks', null, 'lockSendCommand', 'text/html')
}

def lockSendCommand(response, data) {
    log.trace "lockSendCommand:$response.status"
    updateTokenAndCookie(response)

    lockState = 'locking'
    kevoCommandGet("/user/remote_locks/command/remote_lock.json", ['arguments': kevoLockId], "lockVerifyCommand", "text/json")
}

def lockVerifyCommand(response, data) {
    log.trace "lockVerifyCommand:$response.status"
    if (response.status == 200) {
        refresh()
    } else {
        log.error "couldn't determine lock status after unlocking, $e"
        lockState = 'unknown'
    }
}

def refresh() {
    log.trace "refresh"
    lockState = 'logging_in_token'
    state.cookie = ""
    state.token = null
    kevoCommandGet '/login', null, "refreshLogin", "text/html"
}

def refreshLogin(response, data) {
    log.trace "refreshLogin:$response.status"
    lockState = 'logging_in_credentials'
    kevoCommandPost('/signin', [
            "user[username]"    : kevoUsername,
            "user[password]"    : kevoPassword,
            "authenticity_token": state.token,
            "commit"            : "LOGIN",
            "utf8"              : "✓"
    ], 'refreshCredentials', 'text/html')
}

def refreshCredentials(response, data) {
    log.trace "refreshCredentials:$response.status"
    if (response.status == 302 || response.status == 200) {
        log.trace "redirect or 200 from login post, considering successful"
    }
    lockState = "logging_in_verifying"
    kevoCommandGet('/user/locks', null, 'refreshSendCommand', 'text/html')
}

def refreshSendCommand(response, data) {
    log.trace "refreshSendCommand:$response.status"
    updateTokenAndCookie(response)

    lockState = 'refreshing'
    kevoCommandGet("/user/remote_locks/command/lock.json", ['arguments': kevoLockId], "refreshVerifyCommand", "text/json")
}

def refreshVerifyCommand(response, data) {
    log.trace "refreshVerifyCommand:$response.status"
    if (response.status == 200) {
        def json = response.json
        switch (json.bolt_state) {
            case "Locked": lockState = 'locked'; break
            case "Unlocked": lockState = 'unlocked'; break
            case "UnlockedBoltJam": lockState = 'unlocked_jammed'; break
            case "LockedBoltJam": lockState = 'locked_jammed'; break
        }
    } else {
        log.error "couldn't determine lock status after refreshing, $e"
        lockState = 'unknown'
    }

}


def kevoCommandPost(path, body, callback, contentType, passthru = null) {
    log.trace("kevoCommandPost(path:$path, body:$body, callback:$callback, contentType:$contentType, headers: $headers")
    String stringBody = body?.collect { k, v -> "$k=$v" }.join("&")?.toString() ?: ""

    def params = [
            uri               : "https://mykevo.com",
            path              : path,
            body              : stringBody,
            headers           : headers,
            requestContentType: "application/x-www-form-urlencoded",
            contentType       : contentType
    ]

    def data = [
            path       : path,
            contentType: contentType,
            callback   : callback
    ] //Data for Async Command.  Params to retry, handler to handle, and retry count if needed

    try {
        log.debug "Attempting to post: $data"
        asynchttp_v1.post('kevoCommandPostResponseHandler', params, data)
        state.referer = "${params['uri']}$path"
    } catch (e) {
        log.error "Something unexpected went wrong in kevoCommandPost: ${e}"
    }//try / catch for asynchttpPost
}//async post command

def kevoCommandPostResponseHandler(response, data) {
    log.trace "kevoCommandPostResponseHandler:$response.status->$response.headers"
    updateTokenAndCookie(response)
    def callback = data.get('callback')
    switch (callback) {
        case "unlockLogin": unlockLogin(response, data); break
        case "unlockCredentials": unlockCredentials(response, data); break
        case "unlockLoginVerify": unlockLoginVerify(response, data); break
        case "unlockSendCommand": unlockSendCommand(response, data); break
        case "unlockVerifyCommand": unlockVerifyCommand(response, data); break
        case "refreshLogin": refreshLogin(response, data); break
        case "refreshCredentials": refreshCredentials(response, data); break
        case "refreshLoginVerify": refreshLoginVerify(response, data); break
        case "refreshSendCommand": refreshSendCommand(response, data); break
        case "refreshVerifyCommand": refreshVerifyCommand(response, data); break
        case "lockLogin": lockLogin(response, data); break
        case "lockCredentials": lockCredentials(response, data); break
        case "lockLoginVerify": lockLoginVerify(response, data); break
        case "lockSendCommand": lockSendCommand(response, data); break
        case "lockVerifyCommand": lockVerifyCommand(response, data); break
        default: noCallback(response, data); break
    }
}

def kevoCommandGet(path, query, callback, contentType, passthru = null) {
    log.trace "kevoCommandGet(path:$path, query:$query, contentType:$contentType, callback:$callback, headers: $headers)"

    def params = [
            uri               : "https://mykevo.com",
            path              : path,
            query             : query,
            headers           : headers,
            requestContentType: contentType
    ]

    def data = [
            path       : path,
            contentType: contentType,
            callback   : callback,
    ]

    try {
        log.debug("Attempting to get: $data")
        asynchttp_v1.get('kevoCommandGetResponseHandler', params, data)
        state.referer = "${params['uri']}$path"
    } catch (e) {
        log.error "Something unexpected went wrong in kevoCommandGet: ${e}: $e.stackTrace"
    }
}

def kevoCommandGetResponseHandler(response, data) {
    log.trace "kevoCommandGetResponseHandler:$response.status->$response.headers"
    updateTokenAndCookie(response)
    def callback = data.get('callback')
    switch (callback) {
        case "unlockLogin": unlockLogin(response, data); break
        case "unlockCredentials": unlockCredentials(response, data); break
        case "unlockLoginVerify": unlockLoginVerify(response, data); break
        case "unlockSendCommand": unlockSendCommand(response, data); break
        case "unlockVerifyCommand": unlockVerifyCommand(response, data); break
        case "refreshLogin": refreshLogin(response, data); break
        case "refreshCredentials": refreshCredentials(response, data); break
        case "refreshLoginVerify": refreshLoginVerify(response, data); break
        case "refreshSendCommand": refreshSendCommand(response, data); break
        case "refreshVerifyCommand": refreshVerifyCommand(response, data); break
        case "lockLogin": lockLogin(response, data); break
        case "lockCredentials": lockCredentials(response, data); break
        case "lockLoginVerify": lockLoginVerify(response, data); break
        case "lockSendCommand": lockSendCommand(response, data); break
        case "lockVerifyCommand": lockVerifyCommand(response, data); break
        default: noCallback(response, data); break
    }
}

def getHeaders() {
    def headers = [
            "Cookie"       : state.cookie,
            "User-Agent"   : "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.12; rv:52.0) Gecko/20100101 Firefox/52.0",
            "Connection"   : "keep-alive",
            "Cache-Control": "no-cache"
    ]
    if (state.token) {
        headers["Referer"] = state.referer ?: "https://mykevo.com/login"
        headers["X-CSRF-TOKEN"] = state.token
    }
    return headers
}

// Standard Hooks
def installed() {
    log.trace "installed()"
    initialize()
}

def updated() {
    log.trace "updated()"
//    unsubscribe()
//    initialize()
}

def initialize() {
    log.trace "initialize()"

    // Setup the ping
    sendEvent(name: "checkInterval", value: 12 * 60, displayed: false, data: [protocol: "cloud", scheme: "untracked"])
}

def ping() {
//    refresh()
    sendEvent(name: "lock", type: 'refresh', value: device.currentValue("lock"))
}

def updateTokenAndCookie(response) {
//    log.info "headers: $response.headers"
//    log.info "State: $state"
    try {
        state.cookie = response?.headers?.'Set-Cookie'?.split(';')?.getAt(0) ?: state.cookie ?: state.cookie
        state.token = (response.data =~ /meta content="(.*?)" name="csrf-token"/)[0][1]
//        log.trace "found and using authenticity token=$state.token, and cookies=$state.cookie"
    } catch (Exception e) {
        log.warn "Couldn't fetch cookie and token from response, $e"
    }
}

// Standard Metadat Definitions at the bottom since it changes rarely
metadata {
    // Automatically generated. Make future change here.
    definition(name: "Kevo Unofficial", namespace: "kennelbound-smartthings/kevolock", author: "Kennelbound") {
        capability "Actuator"
        capability "Sensor"
        capability "Health Check"

        capability "Lock"
        capability "Refresh"

        command "refresh"
    }

    preferences {
        input name: 'kevoUsername', type: 'email', title: 'Kevo Email', description: 'Username to log into mykevo.com', required: true, displayDuringSetup: true
        input name: 'kevoPassword', type: 'password', title: 'Kevo Password', description: 'Password to log into mykevo.com', required: true, displayDuringSetup: true
        input name: 'kevoLockId', type: 'text', title: 'Lock ID', description: 'Can be found on mykevo.com in lock settings', required: true, displayDuringSetup: true
    }

    tiles {
        multiAttributeTile(name: "toggle", type: "generic", width: 6, height: 4) {
            tileAttribute("device.lock", key: "PRIMARY_CONTROL") {
                attributeState "locked_jammed", label: 'jammed locked', action: "lock.lock", icon: "st.locks.lock.locked", backgroundColor: "#00A0DC", nextState: "unlocking"
                attributeState "locked", label: 'locked', action: "lock.lock", icon: "st.locks.lock.locked", backgroundColor: "#00A0DC", nextState: "unlocking"
                attributeState "unlocked_jammed", label: 'jammed unlocked', action: "lock.lock", icon: "st.locks.lock.unlocked", backgroundColor: "#FFFFFF", nextState: "locking"
                attributeState "unlocked", label: 'unlocked', action: "lock.lock", icon: "st.locks.lock.unlocked", backgroundColor: "#FFFFFF", nextState: "locking"
                attributeState "unknown", label: 'jammed', action: "lock.unknown", icon: "st.secondary.activity", backgroundColor: "#E86D13"
                attributeState "locking", label: 'locking', icon: "st.locks.lock.locked", backgroundColor: "#00A0DC"
                attributeState "unlocking", label: 'unlocking', icon: "st.locks.lock.unlocked", backgroundColor: "#FFFFFF"
                attributeState "refreshing", label: 'refreshing', icon: "st.locks.lock.unlocked", backgroundColor: "#FFFFFF"
                attributeState "logging_in_token", label: 'logging in - getting token', icon: "st.secondary.activity", backgroundColor: "#CCCCC0"
                attributeState "logging_in_credentials", label: 'logging in - submitting credentials', icon: "st.secondary.activity", backgroundColor: "#CCCCC0"
                attributeState "logging_in_verifying", label: 'logging in - verifying ', icon: "st.secondary.activity", backgroundColor: "#CCCCC0"
            }
        }

        standardTile("lock", "device.lock", inactiveLabel: false, decoration: "flat", width: 3, height: 2) {
            state "default", label: 'lock', action: "lock.lock", icon: "st.locks.lock.locked"
        }
        standardTile("unlock", "device.lock", inactiveLabel: false, decoration: "flat", width: 3, height: 2) {
            state "default", label: 'unlock', action: "lock.unlock", icon: "st.locks.lock.unlocked"
        }
        standardTile("refresh", "command.refresh", inactiveLabel: false, decoration: "flat", width: 3, height: 2) {
            state "default", label: 'Refresh', icon:"st.secondary.refresh", backgroundColor: "#CCCCCC"
        }

        main "toggle"
        details(["toggle", "lock", "unlock", "refresh"])
    }
}