boolean getIS_DEBUG_LOGGING_ENABLED() {
    return enableDebugLogging
}

void log_debug(...args) {
    if(IS_DEBUG_LOGGING_ENABLED) {
        log.debug(args)
    }
}