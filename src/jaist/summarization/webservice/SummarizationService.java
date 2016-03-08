package jaist.summarization.webservice;

import jaist.summarization.Parser;

import javax.jws.WebMethod;
import javax.jws.WebService;

/**
 * Created by chientran on 3/6/16.
 */
@WebService
public class SummarizationService {
    @WebMethod(action="summarizeText")
    public String summarizeText(String long_text, int max_words){
        Parser parser = new Parser(max_words);
        parser.processDocument(long_text);

        return parser.generateSummary();
    }
}
