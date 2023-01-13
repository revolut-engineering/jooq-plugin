import spock.lang.Specification

import java.time.Instant
import java.util.concurrent.TimeoutException

import static com.revolut.jooq.WaitForPortStrategy.wait
import static java.time.Duration.ofSeconds
import static java.util.concurrent.Executors.newFixedThreadPool

class WaitForPortStrategySpec extends Specification {

    private String host = "localhost"

    def "should throw exception when port is not available"() {
        given:
        def port = findRandomPort()

        when:
        wait(host, port, ofSeconds(1))

        then:
        def e = thrown(TimeoutException.class)
        e.message == "Database is not available under localhost:${port}"
    }

    def "should do nothing when port is available"() {
        given:
        def port = openSocket()

        when:
        wait(host, port.localPort, ofSeconds(1))

        then:
        noExceptionThrown()

        cleanup:
        port.close()
    }

    def "should wait for port to be open"() {
        given:
        def pool = newFixedThreadPool(1)
        def port = findRandomPort()
        def startupDelay = 5_000

        pool.submit {
            Thread.sleep(startupDelay)
            openSocket(port)
        }

        when:
        def start = Instant.now()
        wait(host, port, ofSeconds(60))

        then:
        noExceptionThrown()
        start.plusMillis(startupDelay) <= Instant.now()
    }

    private static openSocket(port = 0) {
        return new ServerSocket(port)
    }

    private static findRandomPort() {
        def socket = openSocket()
        def port = socket.localPort
        socket.close()
        return port
    }
}