package protocolsupport.injector.pe;

import net.md_5.bungee.BungeeCord;
import raknetserver.RakNetServer;

import java.net.InetSocketAddress;

public class PEProxyServer {

	private final InetSocketAddress host = new InetSocketAddress(BungeeCord.getInstance().getConfig().getListeners().iterator().next().getHost().getHostName(),
		BungeeCord.getInstance().getConfig().getListeners().iterator().next().getQueryPort());

	private final RakNetServer peserver = new RakNetServer(
		host,
		new PEProxyServerInfoHandler(),
		channel -> {
			PEQueryHandler queryHandler = new PEQueryHandler();
			channel.pipeline()
				.addFirst(PEQueryHandler.NAME, queryHandler)
				.addFirst(PEQueryHandler.Writer.NAME, queryHandler.createWriter())
				.addLast(PECompressor.NAME, new PECompressor())
				.addLast(PEDecompressor.NAME, new PEDecompressor())
				.addLast(PEProxyNetworkManager.NAME, new PEProxyNetworkManager());
		}, 0xFE, PERakNetMetrics.INSTANCE
	);

	public void start() {
		peserver.start();
	}

	public void stop() {
		peserver.stop();
	}

}
