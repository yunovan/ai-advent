package com.yunovan.aiadvent.day21;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class Day21PdfWriter {

    private Day21PdfWriter() {
    }

    public static byte[] generate(List<String> lines) {
        StringBuilder content = new StringBuilder("BT\n/F1 11 Tf\n56 770 Td\n");
        for (String line : lines) {
            content.append('(').append(escape(line)).append(") Tj\n0 -16 Td\n");
        }
        content.append("ET\n");
        String contentStream = content.toString();

        List<String> objects = new ArrayList<>();
        objects.add("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        objects.add("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");
        objects.add("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                + "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>\nendobj\n");
        objects.add("4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n");
        objects.add("5 0 obj\n<< /Length " + contentStream.length() + " >>\nstream\n"
                + contentStream + "endstream\nendobj\n");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        write(out, "%PDF-1.4\n");
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            write(out, objects.get(i));
        }
        int xrefOffset = out.size();
        StringBuilder xref = new StringBuilder("xref\n0 " + (objects.size() + 1) + "\n");
        xref.append("0000000000 65535 f \n");
        for (int i = 1; i < offsets.size(); i++) {
            xref.append(String.format("%010d 00000 n \n", offsets.get(i)));
        }
        write(out, xref.toString());
        write(out, "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\n");
        write(out, "startxref\n" + xrefOffset + "\n%%EOF\n");
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.ISO_8859_1);
        out.writeBytes(bytes);
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)");
    }
}