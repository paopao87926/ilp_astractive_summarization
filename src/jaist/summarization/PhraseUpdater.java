package jaist.summarization;

import jaist.summarization.unit.Phrase;

import java.util.List;

/**
 * Created by chientran on 2/27/16.
 */
public class PhraseUpdater extends Thread{
    private List<Phrase> phrases;
    public PhraseUpdater(List<Phrase> phrases){
        this.phrases = phrases;
    }
    public void run(){
        for (Phrase phrase: phrases){
            phrase.generateConcepts();
        }
    }
}
