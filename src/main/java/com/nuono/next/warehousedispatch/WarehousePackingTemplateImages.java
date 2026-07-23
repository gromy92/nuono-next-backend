package com.nuono.next.warehousedispatch;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

final class WarehousePackingTemplateImages {

    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private final Map<String, ImageData> cache = new HashMap<>();

    void add(XSSFWorkbook workbook, XSSFSheet sheet, String imageUrl, int row, int column) {
        if (imageUrl == null || imageUrl.isBlank()) return;
        ImageData image = cached(imageUrl.trim());
        if (image == null) return;
        int pictureIndex = workbook.addPicture(image.bytes, image.type);
        XSSFClientAnchor anchor = new XSSFClientAnchor(0, 0, 0, 0, column, row, column + 1, row + 1);
        anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);
        sheet.createDrawingPatriarch().createPicture(anchor, pictureIndex);
    }

    private ImageData cached(String url) {
        if (!cache.containsKey(url)) cache.put(url, download(url));
        return cache.get(url);
    }

    private ImageData download(String value) {
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !publicHost(uri.getHost())) return null;
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(1500);
            connection.setReadTimeout(2500);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "Nuono-Warehouse-Packing-Export/1.0");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) return null;
            int length = connection.getContentLength();
            if (length > MAX_BYTES) return null;
            byte[] bytes;
            try (InputStream input = connection.getInputStream()) {
                bytes = readLimited(input);
            }
            if (bytes == null) return null;
            String contentType = connection.getContentType();
            if (contentType != null && contentType.toLowerCase().contains("jpeg")) {
                return new ImageData(bytes, Workbook.PICTURE_TYPE_JPEG);
            }
            if (contentType != null && contentType.toLowerCase().contains("png")) {
                return new ImageData(bytes, Workbook.PICTURE_TYPE_PNG);
            }
            return asPng(bytes);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static boolean publicHost(String host) throws IOException {
        if (host == null || host.isBlank()) return false;
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                    || address.isMulticastAddress() || uniqueLocalIpv6(address)) return false;
        }
        return true;
    }

    private static boolean uniqueLocalIpv6(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }

    private static byte[] readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) >= 0) {
            total += count;
            if (total > MAX_BYTES) return null;
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static ImageData asPng(byte[] bytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) return null;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) return null;
            return new ImageData(output.toByteArray(), Workbook.PICTURE_TYPE_PNG);
        }
    }

    private static final class ImageData {
        final byte[] bytes;
        final int type;

        ImageData(byte[] bytes, int type) {
            this.bytes = bytes;
            this.type = type;
        }
    }
}
