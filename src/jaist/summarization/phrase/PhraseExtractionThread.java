package jaist.summarization.phrase;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import jaist.summarization.PhraseMatrix;
import jaist.summarization.unit.Phrase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


class PhraseExtractionThread extends Thread{
    private PhraseMatrix indicatorMatrix;
    private Map<Integer, List<Phrase>> sentenceHash;
    private CoreMap sentence;
    private int sentenceId;

    public PhraseExtractionThread(PhraseMatrix indicatorMatrix, Map<Integer, List<Phrase>> sentenceHash, CoreMap sentence, int sentenceID){
        this.indicatorMatrix = indicatorMatrix;
        this.sentenceHash = sentenceHash;
        this.sentence = sentence;
        this.sentenceId = sentenceID;
    }


}