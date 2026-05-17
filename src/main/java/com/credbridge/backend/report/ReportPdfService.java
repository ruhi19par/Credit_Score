package com.credbridge.backend.report;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class ReportPdfService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    private static final NumberFormat CURRENCY_FORMATTER = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    public byte[] generate(ReportResponseDto report) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 36, 36, 42, 36);
            PdfWriter.getInstance(document, outputStream);

            document.open();
            addTitle(document, report);
            addSummary(document, report);
            addFinancials(document, report);
            addFactors(document, "Positive factors", report.getPositiveFactors());
            addFactors(document, "Risk factors", report.getRiskFactors());
            document.close();

            return outputStream.toByteArray();
        } catch (DocumentException exception) {
            throw new ReportPdfGenerationException("Failed to generate report PDF", exception);
        }
    }

    private void addTitle(Document document, ReportResponseDto report) {
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, Color.BLACK);
        Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);

        document.add(new Paragraph("CredBridge Credit Report", titleFont));
        document.add(new Paragraph("Application #" + report.getApplicationId(), subtitleFont));
        addSpacer(document);
    }

    private void addSummary(Document document, ReportResponseDto report) {
        PdfPTable table = table();
        addRow(table, "Borrower", report.getFullName());
        addRow(table, "Status", value(report.getStatus()));
        addRow(table, "Mode", value(report.getMode()));
        addRow(table, "Created", report.getCreatedAt() == null ? "-" : DATE_FORMATTER.format(report.getCreatedAt()));
        addRow(table, "Score", value(report.getScore()));
        addRow(table, "Risk level", value(report.getRiskLevel()));
        addRow(table, "Requested amount", money(report.getRequestedAmount()));
        addRow(table, "Tenure", value(report.getTenureMonths()) + " months");
        document.add(table);
        addSpacer(document);
    }

    private void addFinancials(Document document, ReportResponseDto report) {
        document.add(sectionTitle("Financial profile"));

        PdfPTable table = table();
        addRow(table, "Employment type", value(report.getEmploymentType()));
        addRow(table, "Monthly income", money(report.getMonthlyIncome()));
        addRow(table, "Monthly expenses", money(report.getMonthlyExpenses()));
        addRow(table, "Existing debt payment", money(report.getExistingDebtPayment()));
        addRow(table, "Repayment history", value(report.getRepaymentHistory()));
        addRow(table, "Income stability", value(report.getIncomeStability()));
        addRow(table, "Debt-to-income ratio", percent(report.getDebtToIncomeRatio()));
        addRow(table, "Expense ratio", percent(report.getExpenseRatio()));
        addRow(table, "Repayment capacity", money(report.getRepaymentCapacity()));
        addRow(table, "Suggested loan limit", money(report.getSuggestedLoanLimit()));
        document.add(table);
        addSpacer(document);
    }

    private void addFactors(Document document, String title, Iterable<String> factors) {
        document.add(sectionTitle(title));

        boolean hasFactors = false;
        for (String factor : factors) {
            hasFactors = true;
            document.add(new Paragraph("- " + factor));
        }
        if (!hasFactors) {
            document.add(new Paragraph("- None"));
        }
        addSpacer(document);
    }

    private Paragraph sectionTitle(String value) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, Color.BLACK);
        return new Paragraph(value, font);
    }

    private PdfPTable table() {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(8);
        return table;
    }

    private void addRow(PdfPTable table, String label, String value) {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);

        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        labelCell.setPadding(8);
        valueCell.setPadding(8);
        labelCell.setBackgroundColor(new Color(245, 247, 251));
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private void addSpacer(Document document) {
        document.add(new Paragraph(" "));
    }

    private String value(Object value) {
        return value == null ? "-" : value.toString().replace('_', ' ');
    }

    private String money(BigDecimal value) {
        return value == null ? "-" : CURRENCY_FORMATTER.format(value);
    }

    private String percent(BigDecimal value) {
        return value == null ? "-" : value + "%";
    }
}
