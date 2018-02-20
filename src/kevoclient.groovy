__HTTP__
__LOGGING__

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