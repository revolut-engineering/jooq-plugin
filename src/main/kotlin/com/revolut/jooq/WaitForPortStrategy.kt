package com.revolut.jooq

import java.io.IOException
import java.net.Socket
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeoutException

class WaitForPortStrategy {
    companion object {
        fun wait(dbHost: String, port: Int, timeout: Duration) {
            val start = Instant.now()
            while (!isPortAvailable(dbHost, port)) {
                if (start.plus(timeout).isBefore(Instant.now())) {
                    throw TimeoutException("Database is not available under ${dbHost}:${port}")
                }
                Thread.sleep(100)
            }
        }

        private fun isPortAvailable(dbHost: String, port: Int): Boolean {
            try {
                Socket(dbHost, port).close()
            } catch (e: IOException) {
                return false
            }
            return true
        }
    }

}