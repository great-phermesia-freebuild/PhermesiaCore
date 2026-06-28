package me.greatphermesia.core.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ChatUtilTest {

    @Test
    public void colorReturnsEmptyStringForNullInput() {
        assertEquals("", ChatUtil.color(null));
    }

    @Test
    public void colorTranslatesAmpersandCodes() {
        assertEquals("\u00A7aHello", ChatUtil.color("&aHello"));
    }

    @Test
    public void plainTextExtractsReadableTextFromComponents() {
        assertEquals("Hello", ChatUtil.plainText(ChatUtil.component("&aHello")));
    }

    @Test
    public void plainPlayerNameReturnsUnknownForNull() {
        assertEquals("Unknown", ChatUtil.plainPlayerName(null));
    }

    @Test
    public void plainPlayerNameReturnsUnknownForBlank() {
        assertEquals("Unknown", ChatUtil.plainPlayerName("   "));
    }

    @Test
    public void plainPlayerNameStripsColorCodes() {
        assertEquals("Erick", ChatUtil.plainPlayerName(ChatUtil.color("&aErick")));
    }
}
