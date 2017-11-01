package edu.hm.ziegler0.documentservice.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.documentservice.DocumentServiceGrpc;
import io.grpc.documentservice.PDFDocument;
import io.grpc.policyservice.Period;
import io.grpc.policyservice.PolicyId;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

/**
 * PolicyService Client
 */
public class DocumentServiceClient {


    private final ManagedChannel channel;
    private final DocumentServiceGrpc.DocumentServiceBlockingStub blockingStub;

    /**
     * Construct client connecting to HelloWorld server at {@code host:port}.
     */
    public DocumentServiceClient(String host, int port) {
        this(ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext(true)
                .build());
    }

    /**
     * Construct client for accessing RouteGuide server using the existing channel.
     */
    DocumentServiceClient(ManagedChannel channel) {
        this.channel = channel;
        blockingStub = DocumentServiceGrpc.newBlockingStub(channel);
    }

    /**
     * Shutdown channel
     * @throws InterruptedException
     */
    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    /**
     * Say hello to server.
     */
    public void getDocumentById(int id) {
        System.out.println("Search for Policy with id " + id + " ...");

        PolicyId policyId = PolicyId.newBuilder().setId(id).build();

        PDFDocument document;
        try {
            document = blockingStub.getBillToPolicy(policyId);
        } catch (StatusRuntimeException e) {
            System.err.println("RPC failed: {0}" + e.getStatus());
            return;
        }


        FileOutputStream fileOutputStream = null;

        try {
            fileOutputStream = new FileOutputStream("bill_overNetwork.pdf");
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            document.getPdfDocument().writeTo(fileOutputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void billsOfPeriod(LocalDateTime from, LocalDateTime to){

        long fromEpochMilli = from.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long toEpochMilli = to.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        Iterator<PDFDocument> pdfDocumentIterator = blockingStub.streamBillsOfPeriod(
                Period.newBuilder().setFrom(fromEpochMilli).setTo(toEpochMilli).build());

        for(int i = 0;pdfDocumentIterator.hasNext();i++){

            PDFDocument pdfDocument = pdfDocumentIterator.next();
            FileOutputStream fileOutputStream = null;

            try {

                fileOutputStream = new FileOutputStream("bill_overNetwork" + i + ".pdf");
                pdfDocument.getPdfDocument().writeTo(fileOutputStream);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * Greet server. If provided, the first element of {@code args} is the name to use in the
     * greeting.
     */
    public static void main(String[] args) throws Exception {

        DocumentServiceClient client = new DocumentServiceClient("localhost", 50033);
        try {

            //client.getDocumentById(2);

            LocalDateTime from = LocalDateTime.parse("2017-07-01T00:00:00");
            LocalDateTime to = LocalDateTime.parse("2017-07-31T00:00:00");
            client.billsOfPeriod(from,to);

        } finally {
            client.shutdown();
        }

    }
}
