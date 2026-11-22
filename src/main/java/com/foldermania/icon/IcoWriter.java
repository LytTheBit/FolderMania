package com.foldermania.icon;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Writes a .ico file whose entries are PNG-compressed (supported since Windows Vista). */
public final class IcoWriter {

    private IcoWriter() {}

    public static void write(Path out, List<BufferedImage> images) throws IOException {
        List<byte[]> pngs = new ArrayList<>();
        int total = 0;
        for (BufferedImage img : images) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            byte[] png = baos.toByteArray();
            pngs.add(png);
            total += png.length;
        }

        int offset = 6 + 16 * images.size();
        ByteBuffer buf = ByteBuffer.allocate(offset + total).order(ByteOrder.LITTLE_ENDIAN);

        // ICONDIR header: reserved, type (1 = icon), image count
        buf.putShort((short) 0).putShort((short) 1).putShort((short) images.size());

        // ICONDIRENTRY for each image
        for (int i = 0; i < images.size(); i++) {
            BufferedImage img = images.get(i);
            byte[] png = pngs.get(i);
            buf.put((byte) (img.getWidth() >= 256 ? 0 : img.getWidth()));   // 0 means 256
            buf.put((byte) (img.getHeight() >= 256 ? 0 : img.getHeight()));
            buf.put((byte) 0);          // palette colors
            buf.put((byte) 0);          // reserved
            buf.putShort((short) 1);    // color planes
            buf.putShort((short) 32);   // bits per pixel
            buf.putInt(png.length);
            buf.putInt(offset);
            offset += png.length;
        }

        for (byte[] png : pngs) {
            buf.put(png);
        }
        Files.write(out, buf.array());
    }
}
