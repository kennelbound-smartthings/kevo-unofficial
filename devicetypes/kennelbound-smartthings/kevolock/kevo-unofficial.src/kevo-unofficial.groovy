// Helpful: http://htmlpreview.github.io/?https://github.com/krlaframboise/Resources/blob/master/SmartThings-Icons.html

/**
 * Metadata / UX
 */
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

/**
 * KEVO Integration and Event Listeners
 */
/* You must define the following methods to use this:

w_http_client_base_uri('post' or 'get', path, body, callback, contentType, passthru)
w_http_client_update_token(response)
w_http_client_post_callback(data.get('callback'), response, data)
w_http_client_get_callback(data.get('callback'), response, data)
w_http_client_referrer(path, body, callback, contentType)

*/
include 'asynchttp_v1'

def w_http_post(path, body, callback, contentType, passthru = null) {
//    log.trace("eeroCommandPost(path:$path, body:$body, callback:$callback, contentType:$contentType, headers: $headers")
    String stringBody = body?.collect { k, v -> "$k=$v" }?.join("&")?.toString() ?: ""

    def params = [
            uri               : w_http_client_base_uri('post', path, body, callback, contentType, passthru),
            path              : path,
            body              : stringBody,
            headers           : w_http_get_headers(path, body, callback, contentType),
            requestContentType: "application/x-www-form-urlencoded",
            contentType       : contentType
    ]

    def data = [
            path       : path,
            passthru   : passthru,
            contentType: contentType,
            callback   : callback
    ] //Data for Async Command.  Params to retry, handler to handle, and retry count if needed

    try {
//        log.debug "Attempting to post: $data"
        asynchttp_v1.post('w_http_post_response', params, data)
        state.referer = "${params['uri']}$path"
    } catch (e) {
        log.error "Something unexpected went wrong in eeroCommandPost: ${e}"
    }//try / catch for asynchttpPost
}//async post command

def w_http_post_response(response, data) {
//    log.trace "eeroCommandPostResponseHandler:$response.status->$response.headers"
    w_http_client_update_token(response, data)
    w_http_client_post_callback(data.get('callback'), response, data)
}

def w_http_get(path, query, callback, contentType, passthru = null) {
//    log.trace "eeroCommandGet(path:$path, query:$query, contentType:$contentType, callback:$callback, headers: $headers)"

    def params = [
            uri               : w_http_client_base_uri('get', path, body, callback, contentType, passthru),
            path              : path,
            query             : query,
            headers           : w_http_get_headers(path, body, callback, contentType),
            requestContentType: contentType
    ]

    def data = [
            path       : path,
            passthru   : passthru,
            contentType: contentType,
            callback   : callback,
    ]

    try {
//        log.debug("Attempting to get: $data")
        asynchttp_v1.get('w_http_get_response', params, data)
        state.referer = "${params['uri']}$path"
    } catch (e) {
        log.error "Something unexpected went wrong in eeroCommandGet: ${e}: $e.stackTrace"
    }
}

def w_http_get_response(response, data) {
//    log.trace "eeroCommandGetResponseHandler:$response.status->$response.headers"
    w_http_client_update_token(response, data)
    w_http_client_get_callback(data.get('callback'), response, data)
}

def w_http_no_callback(response, data) {
    log.error "Couldn't find response for callback ${data.get('callback')}"
}

def w_http_default_token_cookie(response, data) {
    try {
        state.cookie = response?.headers?.'Set-Cookie'?.split(';')?.getAt(0) ?: state.cookie ?: state.cookie
        state.token = (response.data =~ /meta content="(.*?)" name="csrf-token"/)[0][1]
        state.tokenRefresh = now()
    } catch (Exception e) {
//        log.warn "Couldn't fetch cookie and token from response, $e"
    }
}

def w_http_get_default_headers(path, body, callback, contentType) {
    def headers = [
            "Cookie"       : state.cookie,
            "User-Agent"   : "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.12; rv:52.0) Gecko/20100101 Firefox/52.0",
            "Connection"   : "keep-alive",
            "Cache-Control": "no-cache"
    ]
    if (state.token) {
        headers["Referer"] = state.referer ?: w_http_client_referrer(path, body, callback, contentType)
        headers["X-CSRF-TOKEN"] = state.token
    }
    return headers
}

boolean getIS_DEBUG_LOGGING_ENABLED() {
    return enableDebugLogging
}

void log_debug(...args) {
    if(IS_DEBUG_LOGGING_ENABLED) {
        log.debug(args)
    }
}

String w_http_client_base_uri(method, path, body, callback, contentType, passthru) {
    "https://mykevo.com"
}

String w_http_client_referrer(path, body, callback, contentType) {
    "https://mykevo.com/login"
}

void w_http_client_update_token(response, data) {
    w_http_default_token_cookie(response, data)
    updateToken(response, data)
    updateLockState(response, data)
}

void w_http_client_post_callback(callback, response, data) {
    switch (callback) {
        case "loginTest": loginTest(response, data); break
        case "loginStart": loginStart(response, data); break
        case "loginCredentials": loginCredentials(response, data); break
        case "unlockSendCommand": unlockSendCommand(response, data); break
        case "unlockVerifyCommand": unlockVerifyCommand(response, data); break
        case "refreshSendCommand": refreshSendCommand(response, data); break
        case "refreshVerifyCommand": refreshVerifyCommand(response, data); break
        case "lockSendCommand": lockSendCommand(response, data); break
        case "lockVerifyCommand": lockVerifyCommand(response, data); break
        default: w_http_no_callback(response, data); break
    }
}

void w_http_client_get_callback(callback, response, data) {
    switch (callback) {
        case "loginTest": loginTest(response, data); break
        case "loginStart": loginStart(response, data); break
        case "loginCredentials": loginCredentials(response, data); break
        case "unlockSendCommand": unlockSendCommand(response, data); break
        case "unlockVerifyCommand": unlockVerifyCommand(response, data); break
        case "refreshSendCommand": refreshSendCommand(response, data); break
        case "refreshVerifyCommand": refreshVerifyCommand(response, data); break
        case "lockSendCommand": lockSendCommand(response, data); break
        case "lockVerifyCommand": lockVerifyCommand(response, data); break
        default: w_http_no_callback(response, data); break
    }
}

def w_http_get_headers(path, body, callback, contentType) {
    return w_http_get_default_headers(path, body, callback, contentType)
}

def login(callback) {
    log_debug "login:$callback"
    w_http_get '/user/remote_locks/command/lock.json', ["arguments": kevoLockId], "loginTest", "text/json", callback
}

def loginTest(response, data) {
    log_debug "loginTest:$response.status:${data.get('passthru')}"
    if (response.status == 200 && !response.hasError()) {
        // logged in, continue to passthru method
        log_debug "already logged in, calling passthru callback handler: ${data.get('passthru')}:${data.get('callback')}"
        w_http_client_get_callback(data.passthru, response, data)
    } else {
        log_debug "not logged in, redirecting to login start"
        state.cookie = ""
        state.token = null
        w_http_get '/login', null, "loginStart", "text/html", data.get('passthru')
    }
}

def loginStart(response, data) {
    log_debug "loginStart:$response.status:${data.get('passthru')}"
    w_http_post('/signin', [
            "user[username]"    : kevoUsername,
            "user[password]"    : kevoPassword,
            "authenticity_token": state.token,
            "commit"            : "LOGIN",
            "utf8"              : "✓"
    ], 'loginCredentials', 'text/html', data.get('passthru'))
}

def loginCredentials(response, data) {
    log_debug "loginCredentials:$response.status"
    if (response.status == 302 || response.status == 200) {
        log_debug "redirect or 200 from login post, considering successful"
    }
    w_http_get('/user/locks', null, data.get('passthru'), 'text/html')
}

def unlock() {
    log_debug "unlock"
    sendEvent name: 'lock', value: 'unlocking'
    login 'unlockSendCommand'
}

def unlockSendCommand(response, data) {
    log_debug "unlockSendCommand:$response.status"
    state.last_bolt_time = state.bolt_state_time
    w_http_get("/user/remote_locks/command/remote_unlock.json", ['arguments': kevoLockId], "unlockVerifyCommand", "text/json")
}

def unlockVerifyCommand(response, data) {
    log_debug "unlockVerifyCommand:$response.status"
    if (response.status == 200) {
        log_debug "waiting for $LOCK_REFRESH_WAIT seconds before calling refresh"
        runIn LOCK_REFRESH_WAIT, autoRefresh
    } else {
        log.error "couldn't determine lock status after unlocking, $e"
        sendEvent name: 'lock', value: 'unknown'
    }
}

def lock() {
    log_debug "lock"
    sendEvent name: 'lock', value: 'locking'
    login 'lockSendCommand'
}

def lockSendCommand(response, data) {
    log_debug "lockSendCommand:$response.status"
    state.last_bolt_time = state.bolt_state_time
    w_http_get("/user/remote_locks/command/remote_lock.json", ['arguments': kevoLockId], "lockVerifyCommand", "text/json")
}

def lockVerifyCommand(response, data) {
    log_debug "lockVerifyCommand:$response.status"
    if (response.status == 200) {
        log_debug "waiting for $LOCK_REFRESH_WAIT seconds before calling refresh"
        runIn LOCK_REFRESH_WAIT, autoRefresh
    } else {
        log.error "couldn't determine lock status after locking, $e"
        sendEvent name: 'lock', value: 'unknown'
    }
}

def refresh() {
    state.retry_count = 0
    sendEvent name: 'lock', value: 'refreshing'
    executeRefresh()
}

def autoRefresh() {
    state.retry_count = 0
    executeRefresh()
}

def executeRefresh() {
    log_debug "refresh:$state.retry_count"
    if (state.retry_count <= MAX_REFRESH_RETRIES) {
        state.retry_count = state.retry_count + 1
        login 'refreshSendCommand'
    } else {
        log.error "Too many attempts to retry refresh"
    }
}

def refreshSendCommand(response, data) {
    log_debug "refreshSendCommand:$response.status"
    log_debug "current bolt: $state.last_bolt_time && $state.bolt_state_time"
    w_http_get("/user/remote_locks/command/lock.json", ['arguments': kevoLockId], "refreshVerifyCommand", "text/json")
}

def refreshVerifyCommand(response, data) {
    log_debug "refreshVerifyCommand:$response.status->$state.bolt_state_time::::$state.last_bolt_time"
    if (response.status != 200) {
        log.error "couldn't determine lock status after refreshing, $e"
        sendEvent name: 'lock', value: 'unknown'
        return
    }

    if (state.bolt_state_time == state.last_bolt_time) {
        log_debug "Retrying since we have the old state"

        // the operation hasn't completed, retry the refresh in LOCK_REFRESH_WAIT seconds
        runIn LOCK_REFRESH_WAIT, executeRefresh
        return
    }

    log_debug "changed bolt: $state.last_bolt_time && $state.bolt_state_time"
}

def updateToken(response, data) {
    try {
        state.token = (response.data =~ /meta content="(.*?)" name="csrf-token"/)[0][1]
        state.tokenRefresh = now()
    } catch (Exception e) {
        log_debug "WARN: Couldn't fetch cookie and token from response, $e"
    }
}

def updateLockState(response, data) {
    try {
        def json = response.json
        log_debug "updating state from json: $json.bolt_state_time, $json.firmware_version, $json.bolt_state"
        state.bolt_state_time = json.bolt_state_time
        state.firmware_version = json.firmware_version
        if (currentFirmware != state.firmware_version) {
            sendEvent name: 'firmware', value: state.firmware_version
        }
        switch (json.bolt_state) {
            case "Locked": sendEvent name: 'lock', value: 'locked'; break
            case "Unlocked": sendEvent name: 'lock', value: 'unlocked'; break
            case "UnlockedBoltJam": sendEvent name: 'lock', value: 'unlockedJammed'; break
            case "LockedBoltJam": sendEvent name: 'lock', value: 'lockedJammed'; break
        }
    } catch (Exception e) {
        log.warn "Couldn't update state from $e"
    }
    log_debug "Using state:$state"
}

/**
 * Health and Lifecycle
 */
def ping() {
    refresh()
}

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


/**
 * Constants
 */
// Constants must be expressed as methods, by prefacing with get we can use it like a normal static constant
int getLOCK_REFRESH_WAIT() {
    2 // in seconds seconds
}

int getMAX_REFRESH_RETRIES() {
    5 // retries when the bolt state hasn't changed
}

