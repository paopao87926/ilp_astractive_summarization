package jaist.summarization;

import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;
import jaist.summarization.phrase.PhraseExtractor;
import jaist.summarization.unit.Phrase;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.sql.Timestamp;
import java.util.*;
import gurobi.*;
import org.apache.commons.cli.*;

/**
 * Created by chientran on 9/28/15.
 */

public class Parser {
    Properties props = null;
    StanfordCoreNLP pipeline = null;
    PhraseMatrix indicatorMatrix = null;
    PhraseMatrix compatibilityMatrix = null;
    Integer[][] similarityMatrix = null;
    Integer[][] sentenceGenerationMatrix = null;
    List<Document> docs = null;

    PhraseMatrix alternativeVPs = null;
    PhraseMatrix alternativeNPs = null;
    HashMap<String, HashSet<String>> corefs = null;
    HashSet<String> namedEntities = new HashSet<>();

    List<Phrase> nounPhrases;
    List<Phrase> verbPhrases;
    List<Phrase> allPhrases;

    HashMap<Integer, GRBVar> nounVariables;
    HashMap<Integer, GRBVar> verbVariables;
    HashMap<String, GRBVar> gammaVariables;
    HashMap<String, GRBVar> nounToNounVariables;
    HashMap<String, GRBVar> verbToVerbVariables;

    HashSet<String> nouns;
    HashSet<String> verbs;

    static int DEFAULT_MAXIMUM_SENTENCE = 10;
    static double DEFAULT_ALTERNATIVE_VP_THRESHOLD = 0.75;
    static int DEFAULT_MAX_WORD_LENGTH = 100;
    static int MIN_SENTENCE_LENGTH = 5;
    static int MINIMUM_VERB_LENGTH = 2;

    int max_sentence = 10;
    double alternative_vp_threshold = 0.75;
    int max_word_length = 100;

    int threads = 0;

    long previousMarkedTime;

    public Parser(int max_sentence, double alternative_vp_threshold, int max_word_length, int threads){
        this(max_sentence, alternative_vp_threshold, max_word_length);
        this.threads = threads;
    }

    public Parser(int max_sentence, double alternative_vp_threshold, int max_word_length) {
        this.max_sentence = max_sentence;
        this.alternative_vp_threshold = alternative_vp_threshold;
        this.max_word_length = max_word_length;

        this.props = new Properties();
        pipeline = AnnotatorHub.getInstance().getPipeline();
        indicatorMatrix = new PhraseMatrix();
        compatibilityMatrix = new PhraseMatrix();
        alternativeVPs = new PhraseMatrix();
        alternativeNPs = new PhraseMatrix();

        nounPhrases = new ArrayList<>();
        verbPhrases = new ArrayList<>();
        allPhrases = new ArrayList<>();
        corefs = new HashMap<>();

        nouns = new HashSet<>();
        verbs = new HashSet<>();

        docs = new ArrayList<>();
    }

    public Parser(){
        this(DEFAULT_MAXIMUM_SENTENCE, DEFAULT_ALTERNATIVE_VP_THRESHOLD, DEFAULT_MAX_WORD_LENGTH);
    }

    public Parser(int max_words){
        this();
        this.max_word_length = max_words;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Start at: " + System.currentTimeMillis());
        Options options = new Options();

        options.addOption("help", false, "print command usage");
        options.addOption("word_length", true, "maximum word length");
        options.addOption("vp_threshold", true, "Alternative VP threshold");
        options.addOption("max_sent", true, "maximum # of sentences");
        options.addOption("in", true, "input folder containing all text files");
        options.addOption("out", true, "Output file");
        options.addOption("threads", true, "Number of threads");

        CommandLineParser commandLineParser = new DefaultParser();
        CommandLine cmd = commandLineParser.parse(options, args);

        int word_length = DEFAULT_MAX_WORD_LENGTH;

        if (cmd.hasOption("help")){
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( "usage", options );
            return;
        }

        if (cmd.hasOption("word_length")){
            word_length = Integer.parseInt(cmd.getOptionValue("word_length"));
        }

        int sentence_length = DEFAULT_MAXIMUM_SENTENCE;
        if (cmd.hasOption("max_sent")){
            sentence_length = Integer.parseInt(cmd.getOptionValue("max_sent"));
        }

        double vp_threshold = DEFAULT_ALTERNATIVE_VP_THRESHOLD;
        if (cmd.hasOption("vp_threshold")){
            vp_threshold = Double.parseDouble(cmd.getOptionValue("vp_threshold"));
        }

        String outputFile = "sample.txt";
        if (cmd.hasOption("out")){
            outputFile = cmd.getOptionValue("out");
        }

        int threads = 0;
        if (cmd.hasOption("threads")){
            threads = Integer.parseInt(cmd.getOptionValue("threads"));
        }

        String[] folders = cmd.getOptionValue("in").split(",");

        for (String folderName: folders){
            File folder = new File(folderName);
            File[] fileNames = null;

            if (!folder.isDirectory()){
                System.out.println("Single document summarization. For multi-doc summarization, please specify a folder");
                fileNames = new File[]{folder};
            }else{
                fileNames = folder.listFiles();
            }

            Parser parser = new Parser(sentence_length, vp_threshold, word_length, threads);

            System.out.println("Stanford CoreNLP loaded at " + System.currentTimeMillis());

            for (File filepath: fileNames){
                if (filepath.getName().startsWith(".")) continue;
                System.out.println(filepath.getAbsolutePath());

                File file = new File(filepath.getAbsolutePath());
                //String text = IOUtils.slurpFile(file);
                String text = "John was walking on the street. He saw a pretty girl. Mary met John. She was very beautiful. She wanted to be his girl friend.";
                parser.processDocument(text);
            }

            String summary = parser.generateSummary();


            PrintWriter out = null;
            try {
                out = new PrintWriter(folderName.replace("/", "_") + ".txt");
                out.print(summary);
            } catch (FileNotFoundException ex) {
                System.out.println(ex.getMessage());
            } finally {
                out.close();
            }
        }

    }

    public String generateSummary(){
        System.out.println("Start scoring at " + System.currentTimeMillis());
        scorePhrases();
        System.out.println("Finish scoring at " + System.currentTimeMillis());

        removeRedundantCorefs();

        printLog();

        return findOptimalSolution();
    }

    private void buildCompatibilityMatrix() {
        int npLength = this.nounPhrases.size();
        int vpLength = this.verbPhrases.size();

        for (int p = 0; p < npLength; p++) {
            for (int q = 0; q < vpLength; q++) {
                Phrase noun = nounPhrases.get(p);
                Phrase verb = verbPhrases.get(q);

                int related = 0;

                for (int i = 0; i < npLength; i++) {
                    Phrase otherNoun = nounPhrases.get(i);

                    if (alternativeNPs.exists(noun, otherNoun) && indicatorMatrix.exists(otherNoun, verb)) {
                        related = 1;
                        break;
                    }
                }

                if (related == 0){
                    for (int i=0; i<vpLength; i++){
                        Phrase otherVerb = verbPhrases.get(i);

                        if (alternativeVPs.exists(verb, otherVerb) && indicatorMatrix.exists(noun, otherVerb)){
                            related = 1;
                            break;
                        }
                    }
                }

                if (related == 0 && indicatorMatrix.exists(noun, verb)){
                    related = 1;
                }
                compatibilityMatrix.setValue(this.nounPhrases.get(p), this.verbPhrases.get(q), related);
            }
        }
    }

    private String startOptimization() throws GRBException{
        log("Start building optimization model");
        GRBEnv env = new GRBEnv("mip.log");
        GRBModel model = new GRBModel(env);

        //Note: more threads mean you need more memory
        model.getEnv().set(GRB.IntParam.Threads, threads);

        //model.getEnv().set(GRB.IntParam.OutputFlag, 0);

        GRBLinExpr expr = new GRBLinExpr();

        nounVariables = new HashMap<>();
        verbVariables = new HashMap<>();
        gammaVariables = new HashMap<>();
        nounToNounVariables = new HashMap<>();
        verbToVerbVariables = new HashMap<>();

        markTime("building model for optimization");
        for(Phrase noun:nounPhrases){
            GRBVar var = model.addVar(0.0, 1.0, 1.0, GRB.BINARY, "n:" + noun.getId());
            nounVariables.put(noun.getId(), var);

            expr.addTerm(noun.getScore(), var);

            for (Phrase verb: verbPhrases){
                if (compatibilityMatrix.getValue(noun, verb).equals(1)){
                    String key = "gamma:" + buildVariableKey(noun, verb);
                    GRBVar gamma = model.addVar(0.0, 1.0, 1.0, GRB.BINARY, key);

                    gammaVariables.put(key, gamma);
                }
            }
        }

        for (Phrase verb:verbPhrases){
            GRBVar var = model.addVar(0.0, 1.0, 1.0, GRB.BINARY, "v:" + verb.getId());
            verbVariables.put(verb.getId(), var);

            expr.addTerm(verb.getScore(), var);
        }

        for (int i=0; i<nounPhrases.size()-1; i++){
            for (int j=i+1; j<nounPhrases.size(); j++){
                Phrase noun1 = nounPhrases.get(i);
                Phrase noun2 = nounPhrases.get(j);
                String key = buildVariableKey(noun1, noun2);

                GRBVar var = model.addVar(0.0, 1.0, 1.0, GRB.BINARY, "n2n:" + key);
                nounToNounVariables.put(key, var);
                Double score = -(noun1.getScore() + noun2.getScore()) * calculateSimilarity(noun1, noun2);
                expr.addTerm(score, var);
            }
        }

        for (int i=0; i<verbPhrases.size()-1; i++){
            for (int j=i+1; j<verbPhrases.size(); j++){
                Phrase verb1 = verbPhrases.get(i);
                Phrase verb2 = verbPhrases.get(j);
                String key = buildVariableKey(verb1, verb2);

                GRBVar var = model.addVar(0.0, 1.0, 1.0, GRB.BINARY, "v2v:" + key);
                verbToVerbVariables.put(key, var);

                expr.addTerm(-(verb1.getScore() + verb2.getScore()) * calculateSimilarity(verb1, verb2), var);
            }
        }

        model.update();
        model.setObjective(expr, GRB.MAXIMIZE);

        log("Finish setting objective function. Now adding constraints");

        addNPValidityConstraint(model);
        addVPValidityConstraint(model);
        addNotIWithinIConstraint(model, nounPhrases, nounVariables);
        addNotIWithinIConstraint(model, verbPhrases, verbVariables);
        addPhraseCooccurrenceConstraint(model, nounPhrases, nounVariables, nounToNounVariables);
        addPhraseCooccurrenceConstraint(model, verbPhrases, verbVariables, verbToVerbVariables);
        addSentenceNumberConstraint(model, this.max_sentence);
        addShortSentenceAvoidanceConstraint(model, MIN_SENTENCE_LENGTH);
        addPronounAvoidanceConstraint(model);
        addLengthConstraint(model);

        markTime("finish building model for optimization");

        markTime("Start running optimization model");
        model.optimize();
        markTime("Finish running optimization model");

        HashMap<Integer, Phrase> selectedNouns = new HashMap<>();
        HashMap<Integer, Phrase> selectedVerbs = new HashMap<>();

        for (Phrase phrase: nounPhrases){
            GRBVar var = nounVariables.get(phrase.getId());
            double selected = var.get(GRB.DoubleAttr.X);

            if (selected > 0){
                selectedNouns.put(phrase.getId(), phrase);
            }
        }

        for (Phrase phrase: verbPhrases){
            GRBVar var = verbVariables.get(phrase.getId());
            double selected = var.get(GRB.DoubleAttr.X);

            if (selected > 0){
                selectedVerbs.put(phrase.getId(), phrase);
            }
        }

        HashMap<Integer, List<Phrase>> selectedNP = new HashMap<>();

        Map<Integer, String> summarySentences = new TreeMap<>();

        for (String key: gammaVariables.keySet()){
            GRBVar var = gammaVariables.get(key);

            double value = var.get(GRB.DoubleAttr.X);
            if (value > 0){
                String[] data = key.split(":");
                int nounId = Integer.parseInt(data[1]);
                int verbId = Integer.parseInt(data[2]);

                if (!selectedNP.keySet().contains(nounId)){
                    selectedNP.put(nounId, new ArrayList<Phrase>());
                }

                selectedNP.get(nounId).add(selectedVerbs.get(verbId));
            }
        }

        String summary = "";

        for (Integer key: selectedNP.keySet()){
            Phrase nounPhrase = selectedNouns.get(key);
            List<Phrase> phrases = selectedNP.get(key);
            String sentence = nounPhrase.getContent() + " ";
            Integer minID = Integer.MAX_VALUE;

            List<String> verbs = new ArrayList<>();

            Collections.sort(phrases, (a, b) -> a.getId().compareTo(b.getId()));

            for(Phrase p: phrases){
                if (!p.isNP() && minID > p.getId()){
                    minID = p.getId();
                }
                verbs.add(p.getContent());
            }

            sentence += StringUtils.join(verbs, ", ");
            summarySentences.put(minID, sentence);

            System.out.println(sentence);
        }

        for (Map.Entry<Integer, String> entry: summarySentences.entrySet()){
            summary += entry.getValue() + "\n";
        }

        return summary;
    }

    private void addNPValidityConstraint(GRBModel model) throws GRBException{
        GRBLinExpr expr = null;

        // Add NP Validity
        for (Phrase noun: nounPhrases){
            GRBVar nounVariable = nounVariables.get(noun.getId());
            GRBLinExpr nounConstraint = new GRBLinExpr();

            for (Phrase verb : verbPhrases){
                if (compatibilityMatrix.getValue(noun, verb).equals(1)){
                    String key = "gamma:" + buildVariableKey(noun, verb);
                    GRBVar var = gammaVariables.get(key);

                    expr = new GRBLinExpr();

                    expr.addTerm(1.0, nounVariable);
                    expr.addTerm(-1.0, var);

                    model.addConstr(expr, GRB.GREATER_EQUAL, 0.0, "np_validity:" + buildVariableKey(noun, verb));

                    nounConstraint.addTerm(1.0, var);
                }
            }

            nounConstraint.addTerm(-1.0, nounVariable);
            model.addConstr(nounConstraint, GRB.GREATER_EQUAL, 0.0, "np_validity:" + noun.getId());
        }
    }

    private void addVPValidityConstraint(GRBModel model) throws GRBException{
        // Add Verb Legality
        for (Phrase verb: verbPhrases){
            GRBVar verbVar = verbVariables.get(verb.getId());
            GRBLinExpr constr = new GRBLinExpr();
            constr.addTerm(-1.0, verbVar);

            for (Phrase noun: nounPhrases){
                if (compatibilityMatrix.getValue(noun, verb).equals(1)){
                    String key = "gamma:" + buildVariableKey(noun, verb);
                    GRBVar var = gammaVariables.get(key);

                    constr.addTerm(1.0, var);
                }
            }

            model.addConstr(constr, GRB.EQUAL, 0.0, "vp_legality:" + verb.getId());
        }
    }

    private void addNotIWithinIConstraint(GRBModel model, List<Phrase> phrases, HashMap<Integer, GRBVar> variables)
            throws GRBException {
        // Add Not i-within-i constraint
        for (int i=0; i<phrases.size()-1; i++){
            for (int j=i+1; j<phrases.size(); j++){
                Phrase phrase1 = phrases.get(i);
                Phrase phrase2 = phrases.get(j);
                if (phrase1.getId().equals(phrase2.getParentId())){
                    GRBVar var1 = variables.get(phrase1.getId());
                    GRBVar var2 = variables.get(phrase2.getId());

                    GRBLinExpr expr = new GRBLinExpr();
                    expr.addTerm(1.0, var1);
                    expr.addTerm(1.0, var2);

                    model.addConstr(expr, GRB.LESS_EQUAL, 1.0,
                            "i_within_i:" + phrase1.isNP() + ":" + phrase1.getId() + ":" + phrase2.getId());
                }
            }
        }
    }

    private void addPhraseCooccurrenceConstraint(GRBModel model,
                                                 List<Phrase> phrases,
                                                 HashMap<Integer, GRBVar> variables,
                                                 HashMap<String, GRBVar> linkingVariables) throws GRBException {
        for (int i=0; i<phrases.size()-1; i++){
            Phrase phrase_i = phrases.get(i);
            GRBVar a_i = variables.get(phrase_i.getId());

            for (int j=i+1; j<phrases.size(); j++){
                Phrase phrase_j = phrases.get(j);

                String key = buildVariableKey(phrase_i, phrase_j);

                GRBVar a_j = variables.get(phrase_j.getId());

                GRBVar a_ij = linkingVariables.get(key);

                GRBLinExpr expr = new GRBLinExpr();
                expr.addTerm(1.0, a_ij);
                expr.addTerm(-1.0, a_i);
                model.addConstr(expr, GRB.LESS_EQUAL, 0.0, "phrase_coocurrence_1:" + phrase_i.isNP() + key);

                expr = new GRBLinExpr();
                expr.addTerm(1.0, a_ij);
                expr.addTerm(-1.0, a_j);
                model.addConstr(expr, GRB.LESS_EQUAL, 0.0, "phrase_coocurrence_2:" + phrase_i.isNP() + key);

                expr = new GRBLinExpr();
                expr.addTerm(1.0, a_i);
                expr.addTerm(1.0, a_j);
                expr.addTerm(-1.0, a_ij);
                model.addConstr(expr, GRB.LESS_EQUAL, 1.0, "phrase_coocurrence_3:" + phrase_i.isNP() + key);
            }
        }
    }

    private void addSentenceNumberConstraint(GRBModel model, int K) throws GRBException{
        GRBLinExpr expr = new GRBLinExpr();

        for (Phrase phrase: nounPhrases){
            GRBVar var = nounVariables.get(phrase.getId());
            expr.addTerm(1.0, var);
        }

        model.addConstr(expr, GRB.LESS_EQUAL, K, "sentence_number");
    }

    private void addShortSentenceAvoidanceConstraint(GRBModel model, int M) throws GRBException {
        for(Phrase phrase: verbPhrases){
            if (phrase.getSentenceLength() < M || phrase.getWordLength() < MINIMUM_VERB_LENGTH){
                GRBVar var = verbVariables.get(phrase.getId());
                GRBLinExpr expr = new GRBLinExpr();
                expr.addTerm(1.0, var);

                model.addConstr(expr, GRB.EQUAL, 0.0, "short_sent_avoidance:" + phrase.getId());
            }
        }
    }

    private void addPronounAvoidanceConstraint(GRBModel model) throws GRBException{
        for (Phrase phrase: nounPhrases){
            if (phrase.isPronoun()){
                GRBVar var = nounVariables.get(phrase.getId());
                GRBLinExpr expr = new GRBLinExpr();
                expr.addTerm(1.0, var);
                model.addConstr(expr, GRB.EQUAL, 0.0, "pronoun_avoidance:" + phrase.getId());
            }
        }
    }

    private void addLengthConstraint(GRBModel model) throws GRBException{
        GRBLinExpr expr = new GRBLinExpr();

        for (Phrase phrase: nounPhrases){
            GRBVar var = nounVariables.get(phrase.getId());
            expr.addTerm(phrase.getWordLength(), var);
        }

        for (Phrase phrase: verbPhrases){
            GRBVar var = verbVariables.get(phrase.getId());
            expr.addTerm(phrase.getWordLength(), var);
        }

        model.addConstr(expr, GRB.LESS_EQUAL, this.max_word_length, "length_constraint");
    }

    private String buildVariableKey(Phrase a, Phrase b){
        return a.getId() + ":" + b.getId();
    }

    private double calculateSimilarity(Phrase a, Phrase b){
        for(HashSet set:corefs.values()){
            if (set.contains(a.getContent()) && set.contains(b.getContent())){
                return 1.0;
            }
        }

        return calculateJaccardIndex(a, b);
    }

    public void processDocument(String text) {
        Document document = new Document(text);
        this.docs.add(document);
        extractPhrases(document);
        this.corefs.putAll(document.getCoreferences());
    }

    public void extractPhrases(Document document){
        PhraseExtractor extractor = new PhraseExtractor(document, indicatorMatrix);
        List<Phrase> phrases = extractor.extractAllPhrases();

        for (Phrase phrase : phrases) {
            if (phrase.isNP()) {
                nounPhrases.add(phrase);
                nouns.add(phrase.getContent());
            } else {
                verbPhrases.add(phrase);
                verbs.add(phrase.getContent());
            }
            allPhrases.add(phrase);
        }
    }

    public void scorePhrases(){
        int count = 0;
        for(Document doc: this.docs){
            log("Scoring phrases againsts the doc_id " + count);
            PhraseScorer phraseScorer = new PhraseScorer(doc);
            for (Phrase phrase: allPhrases){
                double score = phraseScorer.scorePhrase(phrase);
                phrase.setScore(phrase.getScore() + score);
            }
            count += 1;
        }
    }

    public String findOptimalSolution() {
        try {
            previousMarkedTime = System.currentTimeMillis();
            markTime("start finding alternative NP and VP");
            findAlternativeNPs(nounPhrases, corefs.values());
            findAlternativeVPs(verbPhrases);
            markTime("finish finding alternative NP and VP");

            markTime("building compatibility matrix");
            buildCompatibilityMatrix();
            markTime("finish building compatibility matrix");
            return startOptimization();
        }catch (Exception ex){
            System.out.println("Exception occurred");
            System.out.println(ex.getMessage());
            ex.printStackTrace();
            return "";
        }
    }

    private void writePhrasesToFile(String filename, List<Phrase> phrases) {
        PrintWriter out = null;
        try {
            out = new PrintWriter(filename);
            for (Phrase phrase : phrases) {
                out.println(phrase.getId() + ":"+ phrase.toString());
            }
        } catch (FileNotFoundException ex) {
            System.out.println(ex.getMessage());
        } finally {
            out.close();
        }
    }

    private void writeCorefsToFile(String filename, HashMap<String, HashSet<String>> corefs) {
        PrintWriter out = null;
        try {
            out = new PrintWriter(filename);
            for (String key : corefs.keySet()) {
                for (String ref : corefs.get(key)) {
                    out.println(ref);
                }
                out.println("");
            }
        } catch (FileNotFoundException ex) {
            System.out.println(ex.getMessage());
        } finally {
            out.close();
        }
    }

    private void writeNersToFile(String filename, HashSet<String> ners) {
        PrintWriter out = null;
        try {
            out = new PrintWriter(filename);
            for (String ner : ners) {
                out.println(ner);
            }
        } catch (FileNotFoundException ex) {
            System.out.println(ex.getMessage());
        } finally {
            out.close();
        }
    }

    private void findAlternativeNPs(List<Phrase> nounPhrases, Collection<HashSet<String>> clusters) {
        for (HashSet<String> cluster : clusters) {
            List<Phrase> alternativePhrases = new ArrayList<Phrase>();

            for (String phraseText : cluster) {
                for (Phrase p : nounPhrases) {
                    if (p.getContent().equals(phraseText)) {
                        alternativePhrases.add(p);
                    }
                }
            }

            int len = alternativePhrases.size();

            for (int i = 0; i < len - 1; i++) {
                for (int j = i + 1; j < len; j++) {
                    Phrase a = alternativePhrases.get(i);
                    Phrase b = alternativePhrases.get(j);
                    alternativeNPs.setValue(a, b, 1);
                    alternativeNPs.setValue(b, a, 1);
                }
            }
        }
    }

    private void findAlternativeVPs(List<Phrase> verbPhrases) {
        int len = verbPhrases.size();

        for (int i = 0; i < len - 1; i++) {
            for (int j = i + 1; j < len; j++) {
                Phrase a = verbPhrases.get(i);
                Phrase b = verbPhrases.get(j);

                Double d = calculateJaccardIndex(a, b);
                if (d >= this.alternative_vp_threshold){
                    alternativeVPs.setValue(a, b, d);
                    alternativeVPs.setValue(b, a, d);
                }
            }
        }
    }

    private double calculateJaccardIndex(Phrase a, Phrase b) {
        Set<String> conceptsInA = a.getConcepts();
        Set<String> conceptsInB = b.getConcepts();

        int count = 0;
        Double finalScore = 0.0d;

        for (String concept : conceptsInA) {
            if (conceptsInB.contains(concept)) {
                count++;
            }
        }

        finalScore = (double) count / (conceptsInA.size() + conceptsInB.size() - count);
        if (finalScore.isNaN()){
            return 0.0;
        }

        return finalScore;
    }

    public void printLog(){
        for(Phrase phrase: nounPhrases){
            log(phrase.toString());
        }

        for(Phrase phrase: verbPhrases){
            log(phrase.toString());
        }

        for(String key: corefs.keySet()){
            log(key + ": " + corefs.get(key).toString());
        }

        for(String ner: namedEntities){
            log(ner);
        }
    }
    private void log(String text){
        System.out.println(text);
    }

    private void markTime(String text){
        System.out.println(new Timestamp(System.currentTimeMillis()) + ": " + text);
    }

    public void removeRedundantCorefs(){
        Iterator<Map.Entry<String, HashSet<String>>> iter = corefs.entrySet().iterator();

        while(iter.hasNext()){
            Map.Entry<String, HashSet<String>> entry = iter.next();
            Set<String> mentions = entry.getValue();
            HashSet<String> newMentions = new HashSet<>(mentions);
            for(String mention: mentions){
                if (!nouns.contains(mention)){
                    newMentions.remove(mention);
                }
            }

            if (newMentions.size() < 2){
                iter.remove();
            }else{
                entry.setValue(newMentions);
            }
        }
    }
}
