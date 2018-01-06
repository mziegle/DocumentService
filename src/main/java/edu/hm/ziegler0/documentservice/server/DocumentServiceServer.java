package edu.hm.ziegler0.documentservice.server;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.documentservice.Chunk;
import io.grpc.documentservice.DocumentServiceGrpc;
import io.grpc.documentservice.PDFDocument;
import io.grpc.policyservice.Period;
import io.grpc.policyservice.Policy;
import io.grpc.policyservice.PolicyId;
import io.grpc.policyservice.PolicyServiceGrpc;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by michaelZiegler on 9/17/17.
 */
public class DocumentServiceServer {

    public static final String POLICY_SERVICE_PORT = "POLICY_SERVICE_PORT";
    public static final String POLICY_SERVICE_HOST = "POLICY_SERVICE_HOST";
    public static final String DOCUMENT_SERVICE_PORT = "DOCUMENT_SERVICE_PORT";
    private Server server;

    private final PdfTemplateStamper pdfTemplateStamper;

    private final PolicyServiceGrpc.PolicyServiceBlockingStub policyServiceBlockingStub;
    private final PolicyServiceGrpc.PolicyServiceStub policyServiceStub;

    private int documentServicePort;

    private static final Map<String,String> DEFAULT_VARIABLES = new HashMap<>();
    static {
        DEFAULT_VARIABLES.put(POLICY_SERVICE_HOST,"localhost");
        DEFAULT_VARIABLES.put(POLICY_SERVICE_PORT,"40001");
        DEFAULT_VARIABLES.put(DOCUMENT_SERVICE_PORT,"40002");
    }


    public DocumentServiceServer() {

        Map<String,String> env = System.getenv();

        int policyServicePort = 0;
        String policyServiceHost = null;

        Map<String, String> environmentVariables = System.getenv();
        boolean envNotFound = false;

        if (environmentVariables.containsKey(POLICY_SERVICE_PORT)) {
            policyServicePort = Integer.parseInt(environmentVariables.get(POLICY_SERVICE_PORT));
        } else {
            System.out.printf("did not find property for policy service port");
            envNotFound = true;
        }

        if (environmentVariables.containsKey(POLICY_SERVICE_HOST)) {
            policyServiceHost = environmentVariables.get(POLICY_SERVICE_HOST);
        } else {
            System.out.printf("did not find property for policy service host");
            envNotFound = true;
        }

        if (environmentVariables.containsKey(DOCUMENT_SERVICE_PORT)) {
            documentServicePort = Integer.parseInt(environmentVariables.get(DOCUMENT_SERVICE_PORT));
        } else {
            System.out.printf("did not find property for document service port");
            envNotFound = true;
        }

        if(envNotFound) {

            System.out.println("Okay, using default hosts and ports now.");

            policyServiceHost = DEFAULT_VARIABLES.get(POLICY_SERVICE_HOST);
            policyServicePort = Integer.parseInt(DEFAULT_VARIABLES.get(POLICY_SERVICE_PORT));
            documentServicePort = Integer.parseInt(DEFAULT_VARIABLES.get(DOCUMENT_SERVICE_PORT));

        }

        policyServiceBlockingStub = PolicyServiceGrpc.newBlockingStub(ManagedChannelBuilder
                .forAddress(policyServiceHost,policyServicePort)
                .usePlaintext(true)
                .build());

        policyServiceStub = PolicyServiceGrpc.newStub(ManagedChannelBuilder
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

        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");

        server = ServerBuilder.forPort(documentServicePort)
                .addService(new DocumentServiceGrpc.DocumentServiceImplBase() {

                    @Override
                    public void getBillToPolicy(PolicyId request, StreamObserver<PDFDocument> responseObserver) {

                        Policy policy = policyServiceBlockingStub.getPolicyById(request);

                        responseObserver.onNext(PDFDocument.newBuilder()
                                .setFileName("bill_ID_" + policy.getId() + "_VALIDITY_DATE_" + simpleDateFormat.format(
                                        new Date(policy.getValidityDate())))
                                .setPdfDocument(pdfTemplateStamper.makeBill(policy))
                                .build());
                        responseObserver.onCompleted();

                    }

                    @Override
                    public StreamObserver<Chunk> streamFiles(StreamObserver<Chunk> responseObserver) {

                        System.out.println("Ok is has been called");

                        return new StreamObserver<Chunk>() {

                            @Override
                            public void onNext(Chunk value) {

                                System.out.println(value.getLength() + ":" + value.getBuffer().toString());

                                responseObserver.onNext(Chunk.newBuilder()
                                        .setLength(value.getLength())
                                        .setBuffer(ByteString.copyFrom(value.toByteArray())).build());

                            }

                            @Override
                            public void onError(Throwable t) {
                                t.printStackTrace();
                            }

                            @Override
                            public void onCompleted() {
                                System.out.println("onComplete");
                            }
                        };

                    }

                    @Override
                    public void streamBillsOfPeriod(Period request, StreamObserver<PDFDocument> responseObserver) {

                        policyServiceStub.streamPoliciesByValidityDateBetween (

                            request, new StreamObserver<Policy>() {

                                @Override
                                public void onNext(Policy policy) {

                                    responseObserver.onNext(PDFDocument.newBuilder()
                                            .setFileName("bill_ID_" + policy.getId() + "_VALIDITY_DATE_"
                                                    + simpleDateFormat.format(new Date(policy.getValidityDate())))
                                            .setPdfDocument(pdfTemplateStamper.makeBill(policy))
                                            .build());
                                }

                                @Override
                                public void onError(Throwable t) {
                                    t.printStackTrace();
                                }

                                @Override
                                public void onCompleted() {
                                    responseObserver.onCompleted();
                                }
                            }
                        );
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

    /**
     * Stop the server.
     */
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
