package protocolsupport.injector;

import java.util.Map.Entry;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.ServerConnector;
import net.md_5.bungee.connection.InitialHandler;
import net.md_5.bungee.netty.ChannelWrapper;
import net.md_5.bungee.netty.HandlerBoss;
import net.md_5.bungee.netty.PacketHandler;
import net.md_5.bungee.netty.PipelineUtils;
import net.md_5.bungee.protocol.Varint21LengthFieldPrepender;
import protocolsupport.protocol.ConnectionImpl;
import protocolsupport.protocol.packet.handler.PSInitialHandler;
import protocolsupport.protocol.pipeline.ChannelHandlers;
import protocolsupport.protocol.pipeline.IPipeLineBuilder;
import protocolsupport.protocol.pipeline.common.LogicHandler;
import protocolsupport.protocol.pipeline.initial.InitialPacketDecoder;
import protocolsupport.protocol.storage.ProtocolStorage;
import protocolsupport.utils.ReflectionUtils;
import protocolsupport.utils.netty.ChannelInitializer;

//yep, thats our entry point, a single static field
public class BungeeNettyChannelInjector extends Varint21LengthFieldPrepender {

	private BungeeNettyChannelInjector() {
	}

	public static void inject() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		ReflectionUtils.setStaticFinalField(PipelineUtils.class.getDeclaredField("framePrepender"), new BungeeNettyChannelInjector());
	}

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		super.handlerAdded(ctx);
		ctx.channel().pipeline().addFirst(new ChannelInitializerEntryPoint());
	}

	private static class ChannelInitializerEntryPoint extends ChannelInitializer {

		@Override
		protected void initChannel(Channel channel) throws Exception {
			ChannelPipeline pipeline = channel.pipeline();
			PacketHandler handler = ReflectionUtils.getFieldValue(pipeline.get(HandlerBoss.class), "handler");
			CustomHandlerBoss boss = new CustomHandlerBoss(handler);
			pipeline.replace(PipelineUtils.BOSS_HANDLER, PipelineUtils.BOSS_HANDLER, boss);
			if (handler instanceof InitialHandler) {//user to bungee connection
				Entry<String, ChannelHandler> firstHandler = pipeline.iterator().next();
				if (firstHandler.getValue() instanceof HAProxyMessageDecoder) {
					pipeline.addAfter(firstHandler.getKey(), ChannelHandlers.INITIAL_DECODER, new InitialPacketDecoder());
				} else {
					pipeline.addFirst(ChannelHandlers.INITIAL_DECODER, new InitialPacketDecoder());
				}
				PSInitialHandler initialhandler = new PSInitialHandler(BungeeCord.getInstance(), channel.attr(PipelineUtils.LISTENER).get());
				ConnectionImpl connection = new ConnectionImpl(boss, initialhandler);
				connection.storeInChannel(channel);
				ProtocolStorage.addConnection(channel.remoteAddress(), connection);
				pipeline.addBefore(PipelineUtils.BOSS_HANDLER, ChannelHandlers.LOGIC, new LogicHandler(connection, true));
				pipeline.remove(PipelineUtils.LEGACY_DECODER);
				pipeline.remove(PipelineUtils.LEGACY_KICKER);
				boss.setHandler(initialhandler);
			} else if (handler instanceof ServerConnector) {//bungee to server connection
				ConnectionImpl connection = ConnectionImpl.getFromChannel(((ChannelWrapper) ReflectionUtils.getFieldValue(ReflectionUtils.getFieldValue(handler, "user"), "ch")).getHandle());
				pipeline.addBefore(PipelineUtils.BOSS_HANDLER, ChannelHandlers.LOGIC, new LogicHandler(connection, false));
				connection.setServerConnectionChannel(channel);
				IPipeLineBuilder builder = IPipeLineBuilder.BUILDERS.get(connection.getVersion());
				if (builder != null) {
					builder.buildBungeeServer(channel, connection);
				}
			}
		}

	}

	public static class CustomHandlerBoss extends HandlerBoss {

		protected PacketHandler handler;
		protected PacketHandlerChangeListener listener = (handler) -> handler;

		public CustomHandlerBoss(PacketHandler handler) {
			setHandler(handler);
		}

		@Override
		public void setHandler(PacketHandler handler) {
			handler = listener.onPacketHandlerChange(handler);
			super.setHandler(handler);
			this.handler = handler;
		}

		public PacketHandler getHandler() {
			return this.handler;
		}

		public void setHandlerChangeListener(PacketHandlerChangeListener listener) {
			this.listener = listener;
		}

		@FunctionalInterface
		public interface PacketHandlerChangeListener {
			PacketHandler onPacketHandlerChange(PacketHandler handler);
		}

	}

}
