package com.mohamedopi.pdfbookmarks;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDDocumentCatalog;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_PDF = 101;
    private static final int REQ_FOLDER = 102;

    private TextView selectedFile, status, levelLabel;
    private Spinner levelSpinner;
    private CheckBox keepChildren, newFolder;
    private LinearLayout root;
    private Uri pdfUri, folderUri;
    private String pdfName = "document.pdf";
    private List<Bookmark> bookmarks = new ArrayList<>();
    private List<Bookmark> splitBookmarks = new ArrayList<>();
    private int selectedDepth = 1;
    private boolean processing = false;

    private static class Bookmark {
        String title;
        int page;
        int depth;
        PDOutlineItem source;
        Bookmark parent;
        List<Bookmark> children = new ArrayList<>();
        Bookmark(String title, int page, int depth, PDOutlineItem source, Bookmark parent) {
            this.title = title; this.page = page; this.depth = depth; this.source = source; this.parent = parent;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        PDFBoxResourceLoader.init(getApplicationContext());
        buildUi();
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private TextView text(String value, float size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setTextColor(0xFF0F172A);
        t.setPadding(0, dp(5), 0, dp(5));
        return t;
    }
    private Button button(String label) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false);
        return b;
    }
    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(24));
        root.setBackgroundColor(0xFFF1F5F9);
        scroll.addView(root);
        setContentView(scroll);

        TextView title = text("قصّاص الفهرس (PDF Bookmark Splitter)", 22, true);
        title.setTextColor(0xFF1D4ED8);
        root.addView(title);
        root.addView(text("قسّم ملف PDF حسب عناوين الفهرس، وسمِّ كل ملف باسم الـ Bookmark.", 15, false));
        selectedFile = text("لم يتم اختيار ملف PDF بعد", 14, false);
        root.addView(selectedFile);

        Button choosePdf = button("1. اختيار ملف PDF");
        choosePdf.setOnClickListener(v -> choosePdf());
        root.addView(choosePdf);

        levelLabel = text("2. مستوى التقسيم", 16, true);
        root.addView(levelLabel);
        levelSpinner = new Spinner(this);
        levelSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"المستوى الأول"}));
        levelSpinner.setEnabled(false);
        levelSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                selectedDepth = pos + 1;
                updateSplitBookmarks();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        root.addView(levelSpinner);

        keepChildren = new CheckBox(this);
        keepChildren.setText("الاحتفاظ بالـ Bookmarks الفرعية داخل الملفات الناتجة");
        keepChildren.setChecked(true);
        root.addView(keepChildren);

        newFolder = new CheckBox(this);
        newFolder.setText("وضع الملفات داخل مجلد جديد باسم الملف");
        newFolder.setChecked(false);
        root.addView(newFolder);

        Button chooseFolder = button("3. اختيار مجلد حفظ الملفات");
        chooseFolder.setOnClickListener(v -> chooseFolder());
        root.addView(chooseFolder);

        Button split = button("4. تقسيم ملف PDF");
        split.setTextColor(0xFFFFFFFF);
        split.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2563EB));
        split.setOnClickListener(v -> splitPdf());
        root.addView(split);

        status = text("اختر ملف PDF أولًا. يعمل التطبيق دون إنترنت بعد تثبيت التطبيق وتوفّر الملف.", 14, false);
        status.setPadding(0, dp(14), 0, dp(8));
        root.addView(status);
    }

    private void choosePdf() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/pdf");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_PDF);
    }
    private void chooseFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_FOLDER);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
        } catch (Exception ignored) {}
        if (requestCode == REQ_PDF) {
            pdfUri = uri;
            String name = "document.pdf";
            DocumentFile f = DocumentFile.fromSingleUri(this, uri);
            if (f != null && f.getName() != null) name = f.getName();
            pdfName = name;
            selectedFile.setText("الملف المختار: " + name + "\nجارٍ قراءة الفهرس...");
            loadBookmarks();
        } else if (requestCode == REQ_FOLDER) {
            folderUri = uri;
            DocumentFile f = DocumentFile.fromTreeUri(this, uri);
            status.setText("مجلد الحفظ: " + (f != null ? f.getName() : uri.toString()));
        }
    }

    private void loadBookmarks() {
        if (pdfUri == null) return;
        new Thread(() -> {
            List<Bookmark> found = new ArrayList<>();
            int pages = 0;
            String error = null;
            try (InputStream in = getContentResolver().openInputStream(pdfUri);
                 PDDocument doc = PDDocument.load(in)) {
                pages = doc.getNumberOfPages();
                PDDocumentOutline outline = doc.getDocumentCatalog().getDocumentOutline();
                if (outline != null) {
                    PDOutlineItem item = outline.getFirstChild();
                    while (item != null) {
                        readOutline(item, 1, null, doc, found);
                        item = item.getNextSibling();
                    }
                }
            } catch (Exception e) { error = e.getMessage(); }
            final int pageCount = pages;
            final String err = error;
            runOnUiThread(() -> {
                bookmarks = found;
                if (err != null) {
                    selectedFile.setText("تعذرت قراءة الملف: " + err);
                    status.setText("تأكد أن الملف PDF صالح وغير محمي بكلمة مرور.");
                    return;
                }
                int maxDepth = 0;
                for (Bookmark b : bookmarks) maxDepth = Math.max(maxDepth, b.depth);
                if (bookmarks.isEmpty()) {
                    levelSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                            new String[]{"لا توجد Bookmarks داخل الملف"}));
                    levelSpinner.setEnabled(false);
                    status.setText("عدد الصفحات: " + pageCount + ". لم يتم العثور على Bookmarks؛ لا يمكن التقسيم حسب الفهرس.");
                    return;
                }
                List<String> levels = new ArrayList<>();
                for (int d=1; d<=maxDepth; d++) levels.add("المستوى " + d);
                levelSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, levels));
                levelSpinner.setEnabled(true);
                selectedDepth = 1;
                selectedFile.setText("الملف: " + pdfName + "\nعدد الصفحات: " + pageCount + "\nعدد عناوين الفهرس: " + bookmarks.size());
                updateSplitBookmarks();
                status.setText("تمت قراءة الفهرس. اختر مستوى التقسيم ومجلد الحفظ ثم اضغط تقسيم.");
            });
        }).start();
    }

    private void readOutline(PDOutlineItem item, int depth, Bookmark parent, PDDocument doc, List<Bookmark> all) {
        int page = resolvePage(item, doc);
        Bookmark b = new Bookmark(item.getTitle() == null ? "Bookmark" : item.getTitle(), page, depth, item, parent);
        all.add(b);
        if (parent != null) parent.children.add(b);
        PDOutlineItem child = item.getFirstChild();
        while (child != null) {
            readOutline(child, depth + 1, b, doc, all);
            child = child.getNextSibling();
        }
    }

    private int resolvePage(PDOutlineItem item, PDDocument doc) {
        try {
            PDDestination dest = item.getDestination();
            if (dest == null && item.getAction() instanceof com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo) {
                dest = ((com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo)item.getAction()).getDestination();
            }
            if (dest instanceof com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination) {
                dest = doc.getDocumentCatalog().findNamedDestinationPage(
                    (com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination)dest);
            }
            if (dest instanceof PDPageDestination) {
                PDPageDestination pd = (PDPageDestination)dest;
                PDPage page = pd.getPage();
                if (page != null) {
                    int n = 0;
                    for (PDPage p : doc.getPages()) { if (p == page || p.getCOSObject().equals(page.getCOSObject())) return n; n++; }
                }
                int n = pd.retrievePageNumber();
                if (n >= 0 && n < doc.getNumberOfPages()) return n;
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private void updateSplitBookmarks() {
        splitBookmarks = new ArrayList<>();
        for (Bookmark b : bookmarks) if (b.depth == selectedDepth && b.page >= 0) splitBookmarks.add(b);
        Collections.sort(splitBookmarks, Comparator.comparingInt(a -> a.page));
        if (status != null && !bookmarks.isEmpty()) {
            status.setText("عناوين التقسيم الصالحة في المستوى " + selectedDepth + ": " + splitBookmarks.size());
        }
    }

    private void splitPdf() {
        if (processing) return;
        if (pdfUri == null) { toast("اختر ملف PDF أولًا"); return; }
        if (folderUri == null) { toast("اختر مجلد الحفظ أولًا"); return; }
        updateSplitBookmarks();
        if (splitBookmarks.isEmpty()) { toast("لا توجد عناوين صالحة في هذا المستوى"); return; }
        processing = true;
        status.setText("جارٍ التقسيم... لا تغلق التطبيق.");
        new Thread(() -> {
            int made = 0;
            String error = null;
            try (InputStream in = getContentResolver().openInputStream(pdfUri);
                 PDDocument source = PDDocument.load(in)) {
                DocumentFile rootFolder = DocumentFile.fromTreeUri(this, folderUri);
                if (rootFolder == null || !rootFolder.canWrite()) throw new Exception("تعذر الكتابة في مجلد الحفظ");
                DocumentFile outFolder = rootFolder;
                if (newFolder.isChecked()) {
                    String baseName = cleanName(pdfName.replaceFirst("(?i)\\.pdf$", ""));
                    DocumentFile created = rootFolder.createDirectory(baseName);
                    if (created != null) outFolder = created;
                }
                List<Bookmark> targets = new ArrayList<>(splitBookmarks);
                for (int i=0; i<targets.size(); i++) {
                    Bookmark start = targets.get(i);
                    int startPage = start.page;
                    int endPage = (i + 1 < targets.size()) ? targets.get(i+1).page - 1 : source.getNumberOfPages()-1;
                    if (endPage < startPage) continue;
                    String outName = cleanName(start.title);
                    if (outName.isEmpty()) outName = "Part_" + (i+1);
                    if (outName.toLowerCase(Locale.ROOT).endsWith(".pdf")) outName = outName.substring(0, outName.length()-4);
                    outName = uniqueName(outFolder, outName) + ".pdf";
                    DocumentFile out = outFolder.createFile("application/pdf", outName);
                    if (out == null) throw new Exception("تعذر إنشاء الملف: " + outName);
                    try (PDDocument part = new PDDocument();
                         OutputStream os = getContentResolver().openOutputStream(out.getUri(), "w")) {
                        for (int p=startPage; p<=endPage && p<source.getNumberOfPages(); p++) {
                            part.importPage(source.getPage(p));
                        }
                        if (keepChildren.isChecked()) addOutlineForRange(part, source, start, startPage, endPage);
                        part.save(os);
                    }
                    made++;
                }
            } catch (Exception e) { error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
            final int count = made;
            final String err = error;
            runOnUiThread(() -> {
                processing = false;
                if (err == null) {
                    status.setText("اكتمل التقسيم بنجاح. عدد الملفات الناتجة: " + count);
                    new AlertDialog.Builder(this).setTitle("اكتمل التقسيم")
                            .setMessage("تم إنشاء " + count + " ملف PDF في مجلد الحفظ.")
                            .setPositiveButton("حسنًا", null).show();
                } else {
                    status.setText("حدث خطأ: " + err);
                    new AlertDialog.Builder(this).setTitle("تعذر إكمال التقسيم").setMessage(err)
                            .setPositiveButton("حسنًا", null).show();
                }
            });
        }).start();
    }

    private void addOutlineForRange(PDDocument part, PDDocument source, Bookmark rootBookmark, int startPage, int endPage) {
        try {
            PDDocumentOutline outline = new PDDocumentOutline();
            part.getDocumentCatalog().setDocumentOutline(outline);
            addOutlineNode(part, outline, rootBookmark, startPage, endPage);
            outline.openNode();
        } catch (Exception ignored) {}
    }

    private void addOutlineNode(PDDocument part, PDDocumentOutline outline, Bookmark b, int startPage, int endPage) {
        if (b.page < startPage || b.page > endPage) return;
        PDOutlineItem node = new PDOutlineItem();
        node.setTitle(b.title);
        int local = b.page - startPage;
        if (local >= 0 && local < part.getNumberOfPages()) {
            PDPageFitDestination dest = new PDPageFitDestination();
            dest.setPage(part.getPage(local));
            node.setDestination(dest);
        }
        outline.addLast(node);
        for (Bookmark child : b.children) addOutlineChild(part, node, child, startPage, endPage);
    }

    private void addOutlineChild(PDDocument part, PDOutlineItem parent, Bookmark b, int startPage, int endPage) {
        if (b.page < startPage || b.page > endPage) return;
        PDOutlineItem node = new PDOutlineItem();
        node.setTitle(b.title);
        int local = b.page - startPage;
        if (local >= 0 && local < part.getNumberOfPages()) {
            PDPageFitDestination dest = new PDPageFitDestination();
            dest.setPage(part.getPage(local));
            node.setDestination(dest);
        }
        parent.addLast(node);
        for (Bookmark child : b.children) addOutlineChild(part, node, child, startPage, endPage);
    }

    private String cleanName(String s) {
        if (s == null) return "";
        s = Normalizer.normalize(s.trim(), Normalizer.Form.NFKC);
        s = s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").replaceAll("\\s+", " ");
        if (s.length() > 100) s = s.substring(0,100).trim();
        return s;
    }
    private String uniqueName(DocumentFile folder, String baseName) {
        String candidate = baseName;
        int n = 2;
        while (folder.findFile(candidate + ".pdf") != null) candidate = baseName + " (" + n++ + ")";
        return candidate;
    }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
