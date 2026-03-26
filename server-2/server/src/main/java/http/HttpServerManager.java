package http;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.java_websocket.WebSocket;

import java.util.concurrent.ConcurrentHashMap;

public class HttpServerManager {

    private final Server server;
    private final ConcurrentHashMap<WebSocket, String> roomMapping;

    public HttpServerManager(int port, ConcurrentHashMap<WebSocket, String> roomMapping) {
        this.server = new Server(port);
        this.roomMapping = new ConcurrentHashMap<>();

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);

        context.addServlet(new ServletHolder(new HealthServlet()), "/health");
        context.addServlet(new ServletHolder(new BroadcastServlet(this.roomMapping)), "/internal/broadcast");
        context.addServlet(new ServletHolder(new MetricsServlet()), "/metrics");
    }

    public void start() throws Exception {
        server.start();
        System.out.println("Http server started on port " +
                server.getURI().getPort());
    }

    public void stop() throws Exception {
        server.stop();
    }

    public void join() throws InterruptedException {
        server.join();
    }
}
