package jaist.summarization.utils;

import jaist.summarization.InputDocument;
import jaist.summarization.PhraseMatrix;
import jaist.summarization.unit.Paragraph;
import jaist.summarization.unit.Phrase;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by chientran on 3/15/16.
 */
public class ModelExporter {
    private String documentSetName;
    private String parentFolder;
    public ModelExporter(String statFolder, String documentSetName){
        this.parentFolder = statFolder + "/" + documentSetName + "/";
        File folder = new File(parentFolder);
        if (!folder.exists()){
            folder.mkdirs();
        }
    }

    public void savePhrasesToFile(List<Phrase> phrases){
        try {
            Document doc = generateXmlDocument();
            Element rootElement = doc.createElement("phrases");
            doc.appendChild(rootElement);

            for (Phrase phrase: phrases){
                Element phraseElement = doc.createElement("phrase");
                rootElement.appendChild(phraseElement);

                phraseElement.setAttribute("id", phrase.getId().toString());
                phraseElement.setAttribute("parentId", phrase.getParentId().toString());
                phraseElement.setAttribute("type", phrase.isNP() ? "NP" : "VP");
                phraseElement.setAttribute("sentenceLength", phrase.getSentenceLength().toString());
                phraseElement.setAttribute("length", phrase.getWordLength().toString());

                Element contentElement = doc.createElement("content");
                phraseElement.appendChild(contentElement);
                contentElement.setTextContent(phrase.getContent());

                Element conceptsElement = doc.createElement("concepts");
                phraseElement.appendChild(conceptsElement);
                conceptsElement.setTextContent(String.join(":", phrase.getConcepts()));
            }

            saveXmlToFile(doc, parentFolder + "/phrases.xml");

        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void saveCoreferencesToFile(HashMap<String, HashSet<String>> corefs){
        PrintWriter out = null;
        try {
            out = new PrintWriter(parentFolder + "/corefs.txt");
            for (String key : corefs.keySet()) {
                Set<String> refs = corefs.get(key);
                out.println(key + ":" + String.join("|", refs));
            }
        } catch (FileNotFoundException ex) {
            System.out.println(ex.getMessage());
        } finally {
            out.close();
        }
    }

    public void saveIndicatorMatrixToFile(PhraseMatrix indicatorMatrix){
        PrintWriter out = null;
        try {
            out = new PrintWriter(parentFolder + "/indicator_matrix.txt");
            for (String key : indicatorMatrix.keySet()) {
                Integer value = Integer.parseInt(indicatorMatrix.getValue(key).toString());

                out.println(key + ":" + value);
            }
        } catch (FileNotFoundException ex) {
            System.out.println(ex.getMessage());
        } finally {
            out.close();
        }
    }

    public void saveParagraphsToFile(List<InputDocument> documents){
        try {
            Document xmlDoc = generateXmlDocument();
            Element rootElement = xmlDoc.createElement("docs");
            xmlDoc.appendChild(rootElement);

            for (InputDocument doc : documents) {
                Element docElement = xmlDoc.createElement("doc");
                rootElement.appendChild(docElement);

                for (Paragraph paragraph: doc.getParagraphs()){
                    Element paragraphElement = xmlDoc.createElement("p");
                    docElement.appendChild(paragraphElement);

                    for (String concept: paragraph.getConcepts()){
                        Element conceptElement = xmlDoc.createElement("concept");
                        paragraphElement.appendChild(conceptElement);
                        conceptElement.setAttribute("name", concept);
                        conceptElement.setAttribute("freq", paragraph.countFrequency(concept).toString());
                    }
                }
            }

            saveXmlToFile(xmlDoc, parentFolder + "/docs.xml");
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    private Document generateXmlDocument() throws ParserConfigurationException{
        DocumentBuilderFactory dbFactory =
                DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder =
                dbFactory.newDocumentBuilder();
        return dBuilder.newDocument();
    }

    private void saveXmlToFile(Document xmlDoc, String filename) throws TransformerConfigurationException{
        TransformerFactory transformerFactory =
                TransformerFactory.newInstance();
        Transformer transformer =
                transformerFactory.newTransformer();
        DOMSource source = new DOMSource(xmlDoc);

        StreamResult result = new StreamResult(new File(filename));
        try{
            transformer.transform(source, result);
        }catch(Exception e){
            e.printStackTrace();
        }

    }
}
