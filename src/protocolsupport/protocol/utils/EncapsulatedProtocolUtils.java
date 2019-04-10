package protocolsupport.protocol.utils;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.text.MessageFormat;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import protocolsupport.protocol.serializer.MiscSerializer;
import protocolsupport.protocol.serializer.VarNumberSerializer;

public class EncapsulatedProtocolUtils {

	public static final int FIRST_BYTE = 0;

	private static final int CURRENT_VERSION = 1;

	public static EncapsulatedProtocolInfo readInfo(ByteBuf from) {
		int encapVersion = VarNumberSerializer.readVarInt(from);
		if (encapVersion > CURRENT_VERSION) {
			throw new DecoderException(MessageFormat.format("Unsupported encapsulation protocol version {}", encapVersion));
		}
		InetSocketAddress remoteaddress = null;
		if (from.readBoolean()) {
			try {
				InetAddress address = InetAddress.getByAddress(MiscSerializer.readBytes(from, VarNumberSerializer.readVarInt(from)));
				remoteaddress = new InetSocketAddress(address, VarNumberSerializer.readVarInt(from));
			} catch (UnknownHostException e) {
				throw new DecoderException("Invalid ip address");
			}
		}
		boolean hasCompression = from.readBoolean();
		if (encapVersion == 0) {
			VarNumberSerializer.readVarInt(from);
			VarNumberSerializer.readVarInt(from);
		}
		return new EncapsulatedProtocolInfo(remoteaddress, hasCompression);
	}

	public static void writeInfo(ByteBuf to, EncapsulatedProtocolInfo info) {
		VarNumberSerializer.writeVarInt(to, CURRENT_VERSION);
		if (info.getAddress() != null) {
			to.writeBoolean(true);
			byte[] addr = info.getAddress().getAddress().getAddress();
			VarNumberSerializer.writeVarInt(to, addr.length);
			to.writeBytes(addr);
			VarNumberSerializer.writeVarInt(to, info.getAddress().getPort());
		} else {
			to.writeBoolean(false);
		}
		to.writeBoolean(info.hasCompression());
	}

}
