package protocolsupport.injector.pe;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.EncoderException;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import protocolsupport.api.ProtocolVersion;
import protocolsupport.protocol.serializer.PEPacketIdSerializer;
import protocolsupport.protocol.serializer.StringSerializer;
import protocolsupport.protocol.utils.ProtocolVersionsHelper;
import protocolsupport.utils.PingSerializer;
import protocolsupport.utils.PingSerializer.ServerPing;
import protocolsupport.utils.Utils;
import raknetserver.pipeline.raknet.RakNetPacketConnectionEstablishHandler.PingHandler;

public class PEProxyServerInfoHandler implements PingHandler {

	private static final int statusThreads = Utils.getJavaPropertyValue("pestatusthreads", 2, Integer::parseInt);
	private static final int statusThreadKeepAlive = Utils.getJavaPropertyValue("pestatusthreadskeepalive", 60, Integer::parseInt);

	protected static final Executor statusprocessor = new ThreadPoolExecutor(
		1, statusThreads,
		statusThreadKeepAlive, TimeUnit.SECONDS,
		new LinkedBlockingQueue<Runnable>(),
		r -> new Thread(r, "PEStatusProcessingThread")
	);

	public static final int PACKET_ID = 3;

	protected static AttributeKey<Boolean> sentInfoKey = AttributeKey.valueOf("___PSPEServerInfoSentInfo");

	@Override
	public String getServerInfo(Channel channel) {
		if (Utils.isTrue(channel.attr(sentInfoKey).getAndSet(Boolean.TRUE))) {
			return "";
		}
		try {
			ByteBuf request = Unpooled.buffer();
			PEPacketIdSerializer.writePacketId(request, PACKET_ID);
			PingResponseInterceptor interceptor = new PingResponseInterceptor();
			channel.pipeline().addBefore(PEProxyNetworkManager.NAME, "peproxy-serverinfo", interceptor);
			channel.pipeline().context(interceptor).fireChannelRead(request);
			ServerPing ping = interceptor.response.take();
			//TODO: use new pe ping format
			return String.join(";",
				"MCPE",
				ping.getMotd().toLegacyText().replace(";", ":"),
				String.valueOf(ping.getVersion().getName()),
				ProtocolVersionsHelper.LATEST_PE.getName().replaceFirst("PE-", ""),
				String.valueOf(ping.getPlayers().getOnlineCount()),
				String.valueOf(ping.getPlayers().getMaxPlayers())
			);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void executeHandler(Runnable runnable) {
		statusprocessor.execute(runnable);
	}


	protected static class PingResponseInterceptor extends ChannelOutboundHandlerAdapter {

		protected final LinkedBlockingQueue<ServerPing> response = new LinkedBlockingQueue<>();

		@Override
		public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
			try {
				ByteBuf serverdata = (ByteBuf) msg;
				if (PEPacketIdSerializer.readPacketId(serverdata) != PACKET_ID) {
					throw new EncoderException("Unknown packet sent by server while handling internal pe ping passthrough");
				}
				response.put(PingSerializer.fromJson(StringSerializer.readVarIntUTF8String(serverdata)));
			} finally {
				ReferenceCountUtil.release(msg);
			}
		}

	}

}
