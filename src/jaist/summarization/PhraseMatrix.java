package jaist.summarization;

import jaist.summarization.unit.Phrase;

import java.util.HashMap;
import java.util.Set;

/**
 * Created by chientran on 10/6/15.
 */
public class PhraseMatrix {
    private HashMap<String, Object> matrix;

    public PhraseMatrix(){
        matrix = new HashMap<>();
    }

    public void setValue(Phrase nounPhrase, Phrase verbPhrase, Object value){
        matrix.put(buildKey(nounPhrase, verbPhrase), value);
        //matrix.put(buildKey(verbPhrase, nounPhrase), value);
    }

    public Object getValue(String key){
        return matrix.get(key);
    }

    public Object getValue(Phrase a, Phrase b){
        return matrix.get(buildKey(a, b));
    }

    public boolean exists(Phrase a, Phrase b){
        return matrix.get(buildKey(a, b)) != null;
    }

    private String buildKey(Phrase a, Phrase b){
        String key = "";
        if (a.isNP()){
            key += "NP_";
        }else{
            key += "VP_";
        }

        key += a.getId() + ":";
        if (b.isNP()){
            key += "NP_";
        }else{
            key += "VP_";
        }

        key += b.getId();

        return key;
    }

    public void printOut(){
        for(String key: matrix.keySet()){
            System.out.println(key + " -> " + matrix.get(key));
        }
    }

    public Set<String> keySet(){
        return matrix.keySet();
    }
}
