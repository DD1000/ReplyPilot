package com.contentfoundry.replypilot;

import org.junit.Test;
import static org.junit.Assert.*;

public class ThemePreferencesTest {
    @Test public void savedThemeIsInDocumentBeforeScriptsOrCss(){
        String source="<html data-theme=\"midnight\"><head><meta name=\"theme-color\" content=\"#191F2C\"><script src=\"theme.js\"></script><link href=\"app.css\"></head>";
        String result=ThemePreferences.launchHtml(source,"rose");
        assertTrue(result.startsWith("<html data-theme=\"rose\">"));
        assertTrue(result.contains("content=\"#30232A\""));
        assertTrue(result.contains("<script src=\"theme.js\"></script>"));
    }
    @Test public void untrustedThemeCannotInjectMarkup(){
        assertEquals("midnight",ThemePreferences.theme("\"><script>alert(1)</script>"));
        assertEquals("<html data-theme=\"midnight\">",ThemePreferences.launchHtml("<html data-theme=\"midnight\">","\"><script>alert(1)</script>"));
        assertEquals("midnight",ThemePreferences.theme(null));
    }
    @Test public void allThemesHaveTheirOwnLaunchBackground(){
        java.util.Set<String> colors=new java.util.HashSet<>();
        for(String theme:java.util.List.of("forest","ocean","lavender","rose","sunset","slate","midnight","mocha","mint","plum")){
            assertEquals(theme,ThemePreferences.theme(theme));assertTrue(colors.add(ThemePreferences.background(theme)));
        }
    }
}
