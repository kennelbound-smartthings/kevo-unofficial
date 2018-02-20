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
