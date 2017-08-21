import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by zhilifeng on 8/20/17.
 * Made small change in all.keys: R 37_12 37_31 ==> R 37_12 37_16, since line 37 doesn't have 31 tokens
 */
public class DataProcessing {

    /**
     *
     * @param filename
     * @param tag either S1, S2, or S3
     * @return
     * @throws ParserConfigurationException
     * @throws IOException
     * @throws SAXException
     */
    public static HashMap<String, String> eventCausalityDataToRawText(
            String filename,
            String tag
    ) throws ParserConfigurationException, IOException, SAXException {
        HashMap<String, String> res = new HashMap<>();

        File file = new File(filename);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        String contentWithNewLine = "";
        if (file.isFile()) {
            byte[] encoded = Files.readAllBytes(Paths.get(filename));
            String fileContent = new String(encoded, StandardCharsets.UTF_8);
            Document document = builder.parse(new InputSource(new StringReader(fileContent)));
            Element rootElement = document.getDocumentElement();
            NodeList nodeList = document.getElementsByTagName("*");
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node currentNode = nodeList.item(i);
                if (currentNode.getNodeName().equals(tag)) {
                    contentWithNewLine += currentNode.getTextContent();
                    contentWithNewLine += "\n";
                }
                if (currentNode.getNodeName().indexOf("DOC")!=-1) {
                    Element currElement = (Element) currentNode;
                    String name = currElement.getAttribute("id");
                    String[] nameList = name.split("\\.");
                    String DCT = nameList[0] + "-" + nameList[1] + "-" + nameList[2];
                    res.put("DCT", DCT);
                    res.put("TITLE", name);
                }
            }
        }
        res.put("TEXT", contentWithNewLine);
        return res;
    }

    public static HashMap<String, HashMap<String, String>> eventCausalityDataToRawTextInFolder(String foldername, String tag) throws IOException, ParserConfigurationException, SAXException {
        File folder = new File(foldername);
        File[] listOfFiles = folder.listFiles();
        HashMap<String, HashMap<String, String>> res = new HashMap<>();
        for (File file : listOfFiles) {
            if (file.isFile()) {
                String filepath = file.getCanonicalPath();
                HashMap<String, String> currRes = DataProcessing.eventCausalityDataToRawText(filepath, tag);
                res.put(currRes.get("TITLE"), currRes);
            }
        }
        return res;
    }

    public static void writeRawText(HashMap<String, HashMap<String, String>> res, String outputFolder) {
        File outDir = new File(outputFolder);
        if (!outDir.exists()) {
            outDir.mkdir();
        }
        for (Map.Entry<String, HashMap<String, String>> entry : res.entrySet()) {
            String key = entry.getKey();
            HashMap<String, String> value = entry.getValue();
            String outputFilename = outputFolder + "/" + key;
            try {
                PrintStream ps = new PrintStream(outputFilename);
                ps.print(value.get("TEXT"));
                ps.close();
            } catch (FileNotFoundException e) {
                System.err.println("Unable to open file");
            }
        }
    }

    public static HashMap<String, List<String>> extractRCLinks(String filename) throws ParserConfigurationException, IOException, SAXException {
        HashMap<String, List<String>> res = new HashMap<>();

        File file = new File(filename);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        if (file.isFile()) {
            byte[] encoded = Files.readAllBytes(Paths.get(filename));
            String fileContent = new String(encoded, StandardCharsets.UTF_8);
            Document document = builder.parse(new InputSource(new StringReader(fileContent)));
            Element rootElement = document.getDocumentElement();
            NodeList nodeList = document.getElementsByTagName("*");
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node currentNode = nodeList.item(i);
                if (currentNode.getNodeName().indexOf("DOC")!=-1) {
                    Element currElement = (Element) currentNode;
                    String name = currElement.getAttribute("id");
                    String linkPairs = currentNode.getTextContent().trim();
                    List<String> lines = new ArrayList<>(Arrays.asList(linkPairs.split(System.getProperty("line.separator"))));

                    res.put(name, lines);
                }
            }
        }
        return res;
    }

    /**
     * Preprocess the generated .tml files by ClearTK. There are two things we need to add:
     * 1. DCT as ClearTK adds empty DCT, and 2. add <TEXT></TEXT> tags aroudn the document
     * @param filename
     */
    public static void preprocessing(String filename, String outputFolder) throws Exception{
        File file = new File(filename);
        File outDir = new File(outputFolder);
        if (!outDir.exists()) {
            outDir.mkdir();
        }
        if (file.isFile()) {
            // Add DCT and <TEXT> prefix
            byte[] encoded = Files.readAllBytes(Paths.get(filename));
            String fileContent = new String(encoded, StandardCharsets.UTF_8);
            String dctClause = "<TIMEX3 tid=\"t0\" type=\"\" value=\"\"></TIMEX3><DCT></DCT>";
            int idx = fileContent.indexOf(dctClause);
            String[] splitFilename = filename.split("/");
            String docID = splitFilename[splitFilename.length - 1];
            String[] nameList = docID.split("\\.");
            String DCT = nameList[0] + "-" + nameList[1] + "-" + nameList[2];
            System.out.println(docID);
            String lSubstring = fileContent.substring(0, idx);
            String rSubstring = fileContent.substring(idx + dctClause.length());
            dctClause = String.format(
                    "<DOCID>%s</DOCID>\n<DCT><TIMEX3 tid=\"t0\" type=\"DATE\" value=\"%s\"></TIMEX3></DCT>\n<TEXT>\n",
                    docID,
                    DCT
            );
            String midProcessRes = lSubstring + dctClause + rSubstring;

            // Add </TEXT> suffix
            String makeInstanceClause = "<MAKEINSTANCE";
            idx = midProcessRes.indexOf(makeInstanceClause);
            lSubstring = midProcessRes.substring(0, idx);
            rSubstring = midProcessRes.substring(idx + makeInstanceClause.length());
            makeInstanceClause = "</TEXT>\n<MAKEINSTANCE";
            String finalProcessRes = lSubstring + makeInstanceClause + rSubstring;

            try {
                PrintStream ps = new PrintStream(outputFolder + "/" + docID);
                ps.print(finalProcessRes);
                ps.close();
            } catch (FileNotFoundException e) {
                System.err.println("Unable to open file");
            }
        }
    }

    public static void preprocessingFolder(String foldername, String outputFolder) throws Exception {
        File folder = new File(foldername);
        File[] listOfFiles = folder.listFiles();
        HashMap<String, HashMap<String, String>> res = new HashMap<>();
        for (File file : listOfFiles) {
            if (file.isFile()) {
                preprocessing(file.getCanonicalPath(), outputFolder);
            }
        }
    }


    /**
     * Generate RCLink ds
     * @param extractedRCLinks RCLink get from *.keys files
     * @param tmlFilename the filename of the tml file that contains event chunks
     * @param origTokenizedFile the filename of the original file part that was tagged by <S3></S3>
     * @return
     */
    public static List<RCLink> convertExtractedRCLinkToTimeMLFormatLinks(
            List<String> extractedRCLinks,
            String tmlFilename,
            String origTokenizedFile
    ) throws ParserConfigurationException, IOException, SAXException {
        double numLinkByQuang = 0;
        double numLinkByUs = 0;
        System.out.println(tmlFilename);
        List<RCLink> res = new ArrayList<>();
        int lid = 0;
        File file = new File(tmlFilename);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        String text = "";
        HashMap<String, String> eventInstanceMap = new HashMap<>();
        if (file.isFile()) {
            byte[] encoded = Files.readAllBytes(Paths.get(tmlFilename));
            String fileContent = new String(encoded, StandardCharsets.UTF_8);
            Document document = builder.parse(new InputSource(new StringReader(fileContent)));
            Element rootElement = document.getDocumentElement();
            NodeList nodeList = document.getElementsByTagName("*");
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node currentNode = nodeList.item(i);
                if (currentNode.getNodeName().indexOf("TEXT")!=-1) {
                    text = currentNode.getTextContent();
                }
                if (currentNode.getNodeName().indexOf("MAKEINSTANCE")!=-1) {
                    Element currElement = (Element) currentNode;
                    String eiid = currElement.getAttribute("eiid");
                    String eid = currElement.getAttribute("eventID");
                    eventInstanceMap.put(eid, eiid);
                }
            }
            // This is hacky. Our way of preprocessing the data makes sure that the actual text starts on line 4
            String []temp = fileContent.trim().split(System.getProperty("line.separator"));
            String []lines = Arrays.copyOfRange(temp, 3, temp.length);
            for (String rel : extractedRCLinks) {
                // This is what each rel looks like: C 35_22 35_3
                rel = rel.trim();
                if (rel.length()==0) continue;
                String []relArr = rel.split(" ");
                String type = relArr[0];
                String source = relArr[1];
                String dest = relArr[2];

                //System.out.println(rel);
                String []sourceArr = source.split("_");
                int sourceLineNum = Integer.parseInt(sourceArr[0]);
                int sourceTokenNum = Integer.parseInt(sourceArr[1]);

                String []destArr = dest.split("_");
                int destLineNum = Integer.parseInt(destArr[0]);
                int destTokenNum = Integer.parseInt(destArr[1]);

                String []sourceLine = origTokenizedFile.split(System.getProperty("line.separator"))[sourceLineNum].split(" ");
                String sourceVerb = sourceLine[sourceTokenNum].split("/")[0];
                String sourcePrefix = sourceTokenNum==0?"":sourceLine[sourceTokenNum-1].split("/")[0];
                String sourceSuffix = sourceLine.length >= sourceTokenNum+1?"":sourceLine[sourceTokenNum+1].split("/")[0];

                String []destLine = origTokenizedFile.split(System.getProperty("line.separator"))[destLineNum].split(" ");
                String destVerb = destLine[destTokenNum].split("/")[0];
                String destPrefix = destTokenNum==0?"":destLine[destTokenNum-1].split("/")[0];
                String destSuffix = destLine.length >= destTokenNum+1?"":destLine[destTokenNum+1].split("/")[0];

                String sourceLineInTml = lines[sourceLineNum];
                String destLineInTml = lines[destLineNum];

                String sourceVerbPattern = String.format(
                        "%s\\s*<EVENT eid=\"(e\\d+)\" class=\"\\w+\" tense=\"\\w+\" aspect=\"\\w+\" polarity=\"\\w+\" modality=\"\\w+\">%s</EVENT>\\s*%s",
                        sourcePrefix,
                        sourceVerb,
                        sourceSuffix
                );
                String destVerbPattern = String.format(
                        "%s\\s+<EVENT eid=\"(e\\d+)\" class=\"\\w+\" tense=\"\\w+\" aspect=\"\\w+\" polarity=\"\\w+\" modality=\"\\w+\">%s</EVENT>\\s*%s",
                        destPrefix,
                        destVerb,
                        destSuffix
                );

                String sourceEiid="", destEiid="";

                Pattern r = Pattern.compile(sourceVerbPattern);
                Matcher m = r.matcher(sourceLineInTml);
                boolean found = m.find();
                boolean needToLookMore = false;
                String backupEiid = "";
                if (found) {
                    String eid = m.group(1);
                    sourceEiid = eventInstanceMap.get(eid);
                    while(m.find()) {
                        needToLookMore = true;
                        System.out.println("source " + m.group(1));
                        backupEiid = m.group(1);
                        System.out.println(rel);
                        System.out.println(String.join(" ", Arrays.copyOfRange(sourceLine, sourceTokenNum-2, sourceTokenNum+3)));
                    }
                }

                if (needToLookMore) {
                    int firstOccurrence = String.join(" ", sourceLine).indexOf(String.join(" ", Arrays.copyOfRange(sourceLine, sourceTokenNum-1, sourceTokenNum+2)));
                    int secondOccurrence = String.join(" ", sourceLine).indexOf(String.join(" ", Arrays.copyOfRange(sourceLine, sourceTokenNum-1, sourceTokenNum+2)), firstOccurrence + 1);
                    int realOccurence = String.join(" ", sourceLine).indexOf(String.join(" ", Arrays.copyOfRange(sourceLine, sourceTokenNum-2, sourceTokenNum+3)));
                    if (realOccurence > firstOccurrence) {
                        sourceEiid = backupEiid;
                        System.out.println("should be " + sourceEiid);
                    } else {
                        System.out.println("no change");
                    }
                }
                needToLookMore = false;
                r = Pattern.compile(destVerbPattern);
                m = r.matcher(destLineInTml);

                found = m.find();
                if (found) {
                    String eid = m.group(1);
                    destEiid = eventInstanceMap.get(eid);
                    while(m.find()) {
                        needToLookMore = true;
                        backupEiid = m.group(1);
                        System.out.println("dest " + m.group(1));
                        System.out.println(rel);
                        System.out.println(String.join(" ", Arrays.copyOfRange(destLine, destTokenNum-2, destTokenNum+3)));
                    }
                }
                if (needToLookMore) {
                    int firstOccurrence = String.join(" ", destLine).indexOf(String.join(" ", Arrays.copyOfRange(destLine, destTokenNum-1, destTokenNum+2)));
                    int secondOccurrence = String.join(" ", destLine).indexOf(String.join(" ", Arrays.copyOfRange(destLine, destTokenNum-1, destTokenNum+2)), firstOccurrence + 1);
                    int realOccurence = String.join(" ", destLine).indexOf(String.join(" ", Arrays.copyOfRange(destLine, destTokenNum-2, destTokenNum+3)));
                    if (realOccurence > firstOccurrence) {
                        destEiid = backupEiid;
                        System.out.println("should be " + destEiid);
                    } else {
                        System.out.println("no change");
                    }
                }

                if (sourceEiid != "" && destEiid != "") {
                    RCLink link = new RCLink(type, sourceEiid, destEiid, "l" + lid++);
                    res.add(link);
                    numLinkByUs ++;
                    numLinkByQuang ++;
                } else {
                    numLinkByQuang ++;
                }
            }
        }
        System.out.println("Out links: " + numLinkByUs + "; Quang links: " + numLinkByQuang + "; ratio: " + (1-numLinkByUs/numLinkByQuang));
        return res;
    }

    public static HashMap<String, List<RCLink>> convertExtractedRCLinkToTimeMLFormatLinksInFolder(
            HashMap<String, List<String>> extractedRCLinksMap,
            String tmlFolder,
            HashMap<String, HashMap<String, String>> origTokenizedFileMap
    ) throws IOException, SAXException, ParserConfigurationException {
        HashMap<String, List<RCLink>> res = new HashMap<>();
        for (String filename : extractedRCLinksMap.keySet()) {
            String tmlFilename = tmlFolder + "/" + filename + ".tml";
            List<String> extractedRCLinks = extractedRCLinksMap.get(filename);
            res.put(
                    filename,
                    convertExtractedRCLinkToTimeMLFormatLinks(
                            extractedRCLinks,
                            tmlFilename,
                            origTokenizedFileMap.get(filename).get("TEXT")
                    )
            );
        }
        return res;
    }

    public static void writeRCLinkToTml(String tmlFilename, List<RCLink> RCLinks, String outputFilename) throws ParserConfigurationException, IOException {
        File file = new File(tmlFilename);
        if (file.isFile()) {
            byte[] encoded = Files.readAllBytes(Paths.get(tmlFilename));
            String fileContent = new String(encoded, StandardCharsets.UTF_8);
            String endTimexTag = "</TimeML>";
            int idx = fileContent.indexOf(endTimexTag);
            String lSubstring = fileContent.substring(0, idx);

            String RCLinkString = "";
            for (RCLink link : RCLinks) {
                RCLinkString = RCLinkString + link.toString() + "\n";
            }

            String finalContent = lSubstring + "\n" + RCLinkString + endTimexTag;
            try {
                PrintStream ps = new PrintStream(outputFilename);
                ps.print(finalContent);
                ps.close();
            } catch (FileNotFoundException e) {
                System.err.println("Unable to open file");
            }
        }
    }

    public static void writeRCLinkToTmlInFolder(
            String tmlFoldername,
            HashMap<String, List<RCLink>> RCLinkMap,
            String outputFolder
    ) throws ParserConfigurationException, IOException {
        File outDir = new File(outputFolder);
        if (!outDir.exists()) {
            outDir.mkdir();
        }
        File[] listOfFiles = outDir.listFiles();
        for (String filename : RCLinkMap.keySet()) {
            writeRCLinkToTml(
                    tmlFoldername + "/" + filename + ".tml",
                    RCLinkMap.get(filename),
                    outputFolder + "/" + filename + ".tml"
            );
        }
    }

    public static void main(String []args) throws Exception {
        HashMap<String, HashMap<String, String>> rawText = DataProcessing.eventCausalityDataToRawTextInFolder("./EventCausalityData/all_files", "S3");
        //DataProcessing.writeRawText(res, "./EventCausalityData/raw_text");
        HashMap<String, List<String>> RCLinks = DataProcessing.extractRCLinks("./EventCausalityData/keys/all.keys");
//        DataProcessing.preprocessingFolder(
//                "./EventCausalityData/cleartk_raw_text_casualy_2013/timeml/",
//                "./EventCausalityData/cleartk_raw_text_casualy_2013/processed/"
//        );
//        DataProcessing.convertExtractedRCLinkToTimeMLFormatLinks(
//                RCLinks.get("2010.01.01.iran.moussavi"),
//                "./EventCausalityData/cleartk_raw_text_casualy_2013/processed/2010.01.01.iran.moussavi.tml",
//                rawText.get("2010.01.01.iran.moussavi").get("TEXT")
//        );
        HashMap<String, List<RCLink>> RCLinkMap = DataProcessing.convertExtractedRCLinkToTimeMLFormatLinksInFolder(
                RCLinks,
                "./EventCausalityData/cleartk_raw_text_casualy_2013/processed",
                rawText
        );

        DataProcessing.writeRCLinkToTmlInFolder(
                "./EventCausalityData/cleartk_raw_text_casualy_2013/processed",
                RCLinkMap,
                "./EventCausalityData/cleartk_raw_text_casualy_2013/withRCLink"
        );


    }
}
