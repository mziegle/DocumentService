package edu.hm.ziegler0.documentservice.server;

import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.documentservice.DocumentServiceGrpc;
import io.grpc.documentservice.PDFDocument;
import io.grpc.policyservice.Period;
import io.grpc.policyservice.Policy;
import io.grpc.policyservice.PolicyId;
import io.grpc.policyservice.PolicyServiceGrpc;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;


/**
 * Created by michaelZiegler on 9/17/17.
 */

public class DocumentServiceServer {

    Server server;

    private final int documentServicePort;
    private final String policyServiceHost;
    private final int policyServicePort;


    PdfTemplateStamper pdfTemplateStamper;

    private final PolicyServiceGrpc.PolicyServiceBlockingStub policyServiceBlockingStub;

    public DocumentServiceServer() {

        Map<String,String> env = System.getenv();

        policyServicePort = Integer.parseInt(env.get("POLICY_SERVICE_PORT"));
        policyServiceHost = env.get("POLICY_SERVICE_HOST");
        documentServicePort = Integer.parseInt(env.get("DOCUMENT_SERVICE_PORT"));


        policyServiceBlockingStub = PolicyServiceGrpc.newBlockingStub(ManagedChannelBuilder
                .forAddress(policyServiceHost,policyServicePort)
                .usePlaintext(true)
                .build());

        pdfTemplateStamper = new PdfTemplateStamper();
    }

    /**
     * start the server an register service functions
     * @throws IOException
     */
    private void start() throws IOException {

        server = ServerBuilder.forPort(documentServicePort)
                .addService(new DocumentServiceGrpc.DocumentServiceImplBase() {

                    @Override
                    public void getBillToPolicy(PolicyId request, StreamObserver<PDFDocument> responseObserver) {

                        Policy policy = policyServiceBlockingStub.getPolicyById(request);

                        responseObserver.onNext(PDFDocument.newBuilder()
                                .setPdfDocument(pdfTemplateStamper.makeBill(policy))
                                .build());
                        responseObserver.onCompleted();

                    }

                    @Override
                    public void streamBillsOfPeriod(Period request, StreamObserver<PDFDocument> responseObserver) {

                        Iterator<Policy> policyIterator = policyServiceBlockingStub.streamPoliciesByValidityDateBetween(request);

                        while (policyIterator.hasNext()){
                            Policy policy = policyIterator.next();
                            responseObserver.onNext(PDFDocument.newBuilder()
                                    .setPdfDocument(pdfTemplateStamper.makeBill(policy))
                                    .build());
                        }

                        responseObserver.onCompleted();
                    }
                })
                .build()
                .start();

        System.out.println("Server started, listening on " + documentServicePort);


        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                DocumentServiceServer.this.stop();
                System.err.println("*** server shut down");
             }
        ));
    }

    private void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Main launches the server from the command line.
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        final DocumentServiceServer server = new DocumentServiceServer();
        server.start();
        server.blockUntilShutdown();
    }

}
