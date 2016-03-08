package jaist.summarization.webservice;

import jaist.summarization.AnnotatorHub;

import javax.xml.ws.Endpoint;

/**
 * Created by chientran on 3/6/16.
 */
public class SummarizationServicePublisher {
    static String ADDRESS = "http://localhost:9999/ws/as";

    public static void main(String[] args){

        AnnotatorHub.getInstance();
        Endpoint.publish(ADDRESS, new SummarizationService());
        System.out.println("Web service is ready at: " + ADDRESS);
    }
}
