package jaist.summarization.phrase;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import jaist.summarization.*;
import jaist.summarization.unit.Phrase;
import jaist.summarization.utils.StringUtils;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;

import java.io.Reader;
import java.io.StringReader;
import java.util.*;

public class PhraseExtractor {
    private static String PARSER_MODEL = "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz";
    private static Integer sentenceId = 0;
    private InputDocument inputDocument;
    private PhraseMatrix indicatorMatrix;
    private HashSet<String> namedEntities;


    public PhraseExtractor(InputDocument inputDocument, PhraseMatrix indicatorMatrix){
        this.inputDocument = inputDocument;
        this.indicatorMatrix = indicatorMatrix;
    }

    public List<Phrase> extractAllPhrases() {
        List<Phrase> allPhrases = new ArrayList<>();

        List<CoreMap> sentences = inputDocument.getSentences();
        List<Thread> threads = new ArrayList<>();

        for (CoreMap sentence : sentences) {
            List<Phrase> phrasesInSentence = extractPhrasesFromSentence(sentence);

            allPhrases.addAll(phrasesInSentence);
            for (int i=0; i<phrasesInSentence.size()-1; i++){
                for (int j=i+1; j<phrasesInSentence.size(); j++){
                    Phrase a = phrasesInSentence.get(i);
                    Phrase b = phrasesInSentence.get(j);
                    if (a.isNP() && !b.isNP() && a.getSentenceNodeId() == b.getSentenceNodeId()){
                        indicatorMatrix.setValue(a, b, 1);
                    }
                }
            }
        }

        return allPhrases;
    }

    //Use this if we want a seperate LexicializedParser instead of ParserAnnotation
    private List<Phrase> extractPhrasesWithLexicalParser(String text){
        LexicalizedParser lexicalizedParser = LexicalizedParser.loadModel(PARSER_MODEL);
        List<Phrase> allPhrases = new ArrayList<>();

        Reader reader = new StringReader(text);
        DocumentPreprocessor dp = new DocumentPreprocessor(reader);
        for (List<HasWord> sentence : dp) {
            Tree tree = lexicalizedParser.parse(sentence);
            // ignore the root node
            tree = tree.children()[0];
            allPhrases.addAll(extractSentenceNode(tree, sentence.size()));
        }

        return allPhrases;
    }

    private String getPhrase(Tree tree) {
        List<String> words = new ArrayList<String>();
        List<Tree> leaves = tree.getLeaves();
        for (Tree leaf : leaves) {
            words.add(leaf.value());
        }

        return String.join(" ", words);
    }

    private Integer getSentenceID() {
        sentenceId += 1;

        return sentenceId;
    }

    private List<Phrase> extractPhrasesFromSentence(CoreMap sentence){
        int sentenceLength = StringUtils.countWords(sentence.toString());

        List<Phrase> phrases = new ArrayList<Phrase>();

        Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);

        // ignore the root node
        tree = tree.children()[0];
        phrases.addAll(extractSentenceNode(tree, sentenceLength));

        return phrases;
    }

    private List<Phrase> extractSentenceNode(Tree rootNode, Integer sentenceLength){
        List<Phrase> phrases = new ArrayList<Phrase>();

        int s_length = 0;
        List<Phrase> tempPhrases = new ArrayList<>();
        int sentenceNodeID = 0;
        for (Tree child : rootNode.children()) {
            String nodeValue = child.value();

            if (nodeValue.equals("NP") || nodeValue.equals("VP") || nodeValue.equals("S") || nodeValue.equals("SBAR")) {
                Boolean isNP = !nodeValue.equals("VP");

                String phraseContent = getPhrase(child);

                Phrase phrase = buildPhrase(phraseContent, isNP, -1, 0);
                phrase.setSentenceLength(sentenceLength);

                if (nodeValue.equals("NP") || nodeValue.equals("VP")){
                    s_length += phrase.getWordLength();
                    tempPhrases.add(phrase);
                }

                if (nodeValue.equals("S") || nodeValue.equals("SBAR")){
                    sentenceLength = phrase.getWordLength();
                }

                phrases.add(phrase);

                // expand one step further
                Boolean shouldExpand = true;

                // do not expand VP node if it does not have more than one sub-VPs
                if (!isNP){
                    int subVPCount = 0;
                    for (Tree subTree: child.children()){
                        if (subTree.value().equals("VP")){
                            subVPCount += 1;
                        }
                    }

                    if (subVPCount < 2){
                        continue;
                    }

                    String firstChildLabel = child.getChild(0).value();
                    if (firstChildLabel.equals("MD") || firstChildLabel.equals("VBZ")
                            || firstChildLabel.equals("VBP") || firstChildLabel.equals("VBD")){
                        continue;
                    }
                }

                if (nodeValue.equals("S") || nodeValue.equals("SBAR")){
                    sentenceNodeID += 1;
                }

                for (Tree subChild: child.children()){
                    String subchildValue = subChild.value();

                    if ((isNP && subchildValue.equals("NP")) || (!isNP && subchildValue.equals("VP"))){
                        Phrase subPhrase = buildPhrase(getPhrase(subChild), isNP, phrase.getId(), sentenceNodeID);
                        subPhrase.setSentenceLength(sentenceLength);
                        phrases.add(subPhrase);
                    }
                }
            }
        }

        for(Phrase p: tempPhrases){
            p.setSentenceLength(s_length);
        }
        return phrases;
    }

    private Phrase buildPhrase(String content, boolean isNP, int parentID, int sentenceNodeID){
        Set<String> concepts = inputDocument.extractConceptsFromString(content).keySet();

        Phrase p = new Phrase(content, isNP, parentID, sentenceNodeID);
        p.setConcepts(concepts);

        return p;
    }

    public static void main(String[] args) throws Exception {
        String parserModel = "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz";
        LexicalizedParser lp = LexicalizedParser.loadModel(parserModel);

        Options options = new Options();
        options.addOption("in", true, "input folder containing all text files");
        CommandLineParser commandLineParser = new DefaultParser();
        CommandLine cmd = commandLineParser.parse(options, args);

        String filePath = cmd.getOptionValue("in");
        //File file = new File(filePath);
        //String text = IOUtils.slurpFile(file);
        String text = "And the better shape Clinton is in as his term ends, the better chance Gore, his sidekick for six years now, stands in the presidential nomination process and, ultimately, the election.";

        InputDocument doc = new InputDocument(text);

        PhraseMatrix indicatorMatrix = new PhraseMatrix();
        List<Phrase> phrases = new PhraseExtractor(doc, indicatorMatrix).extractAllPhrases();

        for (Phrase p: phrases){
            System.out.println(p.toString());
        }
    }

}
