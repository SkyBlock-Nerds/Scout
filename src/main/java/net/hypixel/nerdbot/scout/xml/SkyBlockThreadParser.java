package net.hypixel.nerdbot.scout.xml;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

@Slf4j
public class SkyBlockThreadParser {

    private SkyBlockThreadParser() {
    }

    public static List<HypixelThread> parseSkyBlockThreads(String xml) {
        SAXParserFactory factory = SAXParserFactory.newInstance();

        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            SAXParser saxParser = factory.newSAXParser();
            SkyBlockThreadHandler skyblockThreadHandler = new SkyBlockThreadHandler();
            InputStream targetStream = new ByteArrayInputStream(xml.getBytes());
            saxParser.parse(targetStream, skyblockThreadHandler);

            List<HypixelThread> threadList = skyblockThreadHandler.getSkyBlockForum().getThreadList();
            if (threadList == null) {
                return Collections.emptyList();
            }

            // A thread whose guid could not be read cannot take part in the watcher's ordering
            return threadList.stream()
                .filter(thread -> thread.getGuid() > 0)
                .toList();
        } catch (ParserConfigurationException | SAXException | IOException exception) {
            log.warn("Failed to parse content from: {}", xml, exception);
        } catch (RuntimeException exception) {
            log.error("Unexpected error parsing feed content from: {}", xml, exception);
        }

        return Collections.emptyList();
    }

    @Getter
    @Setter
    public static class SkyBlockForum {
        private boolean finishedReadingPrelude = false;
        private List<HypixelThread> threadList;
    }

    @Getter
    @Setter
    public static class HypixelThread {
        private String title;
        private String publicationDate;
        private String link;
        private int guid;
        private String creator;
        private String forum;
    }
}
