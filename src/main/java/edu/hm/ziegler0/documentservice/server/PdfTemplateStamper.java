package edu.hm.ziegler0.documentservice.server;

import com.google.protobuf.ByteString;
import io.grpc.partnerservice.Customer;
import io.grpc.policyservice.Contract;
import io.grpc.policyservice.Policy;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class PdfTemplateStamper {

    private static final int MAX_AMOUNT_OF_CONTRACTS = 7;
    public static final double GERMAN_TAX_FACTOR = 0.19;

    private byte[] template;

    public PdfTemplateStamper() {

        File file = new File("./bill.pdf");
        template = new byte[0];

        try {
            template = Files.readAllBytes(Paths.get(file.toURI()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ByteString makeBill(final Policy policy){

        PDDocument pdfDocument = null;

        try {
            pdfDocument = PDDocument.load(template);
        } catch (IOException e) {
            e.printStackTrace();
        }

        PDDocumentCatalog docCatalog = pdfDocument.getDocumentCatalog();
        PDAcroForm acroForm = docCatalog.getAcroForm();

        try {

            Customer customer = policy.getCustomer();

            acroForm.getField("firstName").setValue(customer.getFirstName());
            acroForm.getField("lastName").setValue(customer.getLastName());
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd");
            acroForm.getField("date").setValue(simpleDateFormat.format(new Date()));
            acroForm.getField("address").setValue(customer.getAddress());
            acroForm.getField("city").setValue(customer.getCity());
            acroForm.getField("zip").setValue(String.valueOf(customer.getZip()));
            acroForm.getField("state").setValue(customer.getState());
            acroForm.getField("country").setValue(customer.getCountry());

            List<Contract> contractsList = policy.getContractsList();

            double subTotal = 0;

            for(int i = 0; i < contractsList.size() && i <= MAX_AMOUNT_OF_CONTRACTS; i++){
                Contract contract = contractsList.get(i);
                acroForm.getField("contractId" + i).setValue(String.valueOf(contract.getId()));
                acroForm.getField("insurance" + i).setValue(contract.getType());
                acroForm.getField("dueDate" + i).setValue(simpleDateFormat.format(policy.getValidityDate()));
                acroForm.getField("annualSubscription" + i).setValue(String.valueOf(contract.getAnnualSubscription()));
                subTotal += contract.getAnnualSubscription();
            }

            acroForm.getField("subTotal").setValue(String.valueOf(subTotal));
            double tax = subTotal * GERMAN_TAX_FACTOR;
            acroForm.getField("tax").setValue(String.valueOf(round(tax,2)));
            acroForm.getField("totalPayable").setValue(String.valueOf(round(subTotal+tax,2)));

            acroForm.getFields().forEach((field) -> field.setReadOnly(true));

        } catch (IOException e) {
            e.printStackTrace();
        }

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            pdfDocument.save(byteArrayOutputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }

        byte[] bytes = byteArrayOutputStream.toByteArray();

        ByteString byteString = ByteString.copyFrom(bytes);

        return byteString;
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        BigDecimal bigDecimal = new BigDecimal(value);
        bigDecimal = bigDecimal.setScale(places, RoundingMode.HALF_UP);
        return bigDecimal.doubleValue();
    }

}
