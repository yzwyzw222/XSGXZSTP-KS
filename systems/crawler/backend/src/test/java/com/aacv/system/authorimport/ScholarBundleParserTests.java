package com.aacv.system.authorimport;

import static org.junit.jupiter.api.Assertions.*;

import com.aacv.system.authorimport.application.ScholarBundleParser;
import com.aacv.system.authorimport.application.ScholarImportParser;
import com.aacv.system.authorimport.domain.ImportBundle;
import com.aacv.system.authorimport.infrastructure.ScholarTableReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ScholarBundleParserTests {
    private static final String HEADER = "SrcDatabase-来源库,Title-题名,Author-作者,Organ-单位\n";
    private final ScholarBundleParser parser = new ScholarBundleParser(new ScholarImportParser(new ScholarTableReader(), JsonMapper.builder().build()), JsonMapper.builder().build());

    private ScholarBundleParser.FileData file(String name, String rows) {
        return new ScholarBundleParser.FileData(name, (HEADER + rows).getBytes(StandardCharsets.UTF_8));
    }
    private ImportBundle.Options options(int count, String name) {
        return new ImportBundle.Options(name, java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new ImportBundle.FileSettings(0, 1, Map.of())).toList());
    }

    @Test
    void identifiesCommonAuthorAndSplitsDegreesEvenWhenUploadedBeforeAuthoredWorks() {
        var files = List.of(file("学位论文.csv", "硕士,硕士研究,学生甲,甲大学\n博士,博士研究,学生乙,乙大学"),
                file("成果.csv", "期刊,论文甲,张三;李四,甲大学;合作大学\n专利,发明甲,王五;张三,乙大学"));
        var result = parser.parse(files, options(2, "")).preview();
        assertTrue(result.canConfirm());
        assertEquals("张三", result.scholarName());
        assertEquals(List.of("张三"), result.candidates());
        assertEquals(Set.of("甲大学", "乙大学", "合作大学"), Set.copyOf(result.organizations()));
        assertEquals(List.of("MASTER_SUPERVISION", "DOCTOR_SUPERVISION"), result.files().getFirst().modes());
        assertEquals(4, result.validRows());
        assertTrue(result.messages().stream().anyMatch(message -> message.contains("导师姓名筛选")));
        var replay = parser.parse(files, new ImportBundle.Options("张三", result.files().stream()
                .map(item -> new ImportBundle.FileSettings(0, 1, item.preview().mapping())).toList()));
        assertEquals(result.previewKey(), replay.preview().previewKey());
        assertTrue(replay.groups().stream().allMatch(group -> group.options().scholarOrganization().isEmpty()));
    }

    @Test
    void ambiguousCoauthorsRequireSelectionFromTheFileAndRejectFabricatedNames() {
        var files = List.of(file("成果.csv", "期刊,论文,张三;李四,甲大学\n专利,发明,李四;张三,甲大学"));
        var ambiguous = parser.parse(files, options(1, "")).preview();
        assertFalse(ambiguous.canConfirm());
        assertEquals("", ambiguous.scholarName());
        assertEquals(Set.of("张三", "李四"), Set.copyOf(ambiguous.candidates()));
        assertTrue(parser.parse(files, options(1, "李四")).preview().canConfirm());
        assertThrows(IllegalArgumentException.class, () -> parser.parse(files, options(1, "王五")));
        assertNotEquals(parser.parse(files, options(1, "李四")).preview().previewKey(), parser.parse(files, options(1, "张三")).preview().previewKey());
    }

    @Test
    void degreeOnlyAndDisjointAuthorFilesCannotChooseAStudentOrMostFrequentName() {
        var degrees = parser.parse(List.of(file("硕论.csv", "硕士,论文,学生甲,甲大学")), options(1, "")).preview();
        assertFalse(degrees.canConfirm());
        assertTrue(degrees.candidates().isEmpty());
        assertTrue(degrees.messages().getFirst().contains("本人署名成果表"));
        var mixed = parser.parse(List.of(file("混合.csv", "期刊,论文一,张三,甲大学\n期刊,论文二,张三,甲大学\n期刊,论文三,李四,甲大学")), options(1, "")).preview();
        assertFalse(mixed.canConfirm());
        assertTrue(mixed.candidates().isEmpty());
    }

    @Test
    void invalidRowsOutsideTheSampleAndAuthoredDegreesBlockTheWholeBundle() {
        String rows = java.util.stream.IntStream.range(0, 21).mapToObj(i -> "期刊,论文" + i + ",张三,甲大学\n").collect(java.util.stream.Collectors.joining());
        var preview = parser.parse(List.of(file("成果.csv", rows + "期刊,,张三,甲大学")), options(1, "")).preview();
        assertEquals(20, preview.files().getFirst().preview().rows().size());
        assertFalse(preview.canConfirm());
        assertTrue(preview.files().getFirst().preview().issues().stream().anyMatch(issue -> issue.rowNumber() == 23 && !issue.errors().isEmpty()));
        var authoredDegree = parser.parse(List.of(file("成果.csv", "期刊,本人论文,张三,甲大学\n硕士,本人学位论文,张三,甲大学")), options(1, "")).preview();
        assertFalse(authoredDegree.canConfirm());
        assertTrue(authoredDegree.files().getFirst().preview().issues().stream().anyMatch(issue -> issue.errors().stream().anyMatch(error -> error.contains("指导关系"))));
    }

    @Test
    void sourceAndCompleteAuthorsAreRequiredAndDuplicatesAndLimitsAreRejected() {
        var missingSource = parser.parse(List.of(file("成果.csv", ",论文,张三,甲大学")), options(1, "")).preview();
        assertFalse(missingSource.canConfirm());
        var incomplete = new ScholarBundleParser.FileData("不完整.csv", "SrcDatabase,Title,FirstDuty\n期刊,论文,张三".getBytes(StandardCharsets.UTF_8));
        assertFalse(parser.parse(List.of(incomplete), options(1, "")).preview().canConfirm());
        var source = file("成果.csv", "期刊,论文,张三,甲大学");
        assertThrows(IllegalArgumentException.class, () -> parser.parse(List.of(source, source), options(2, "")));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(List.of(source), options(2, "")));
        assertThrows(IllegalArgumentException.class, () -> new ImportBundle.Options("", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ImportBundle.FileSettings(-1, 1, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(List.of(new ScholarBundleParser.FileData("大表.csv", new byte[ScholarTableReader.MAX_BYTES + 1])), options(1, "")));
        String rows = "期刊,论文,张三,甲大学\n".repeat(1001);
        assertThrows(IllegalArgumentException.class, () -> parser.parse(List.of(file("一.csv", rows), file("二.csv", rows + "期刊,另一论文,张三,甲大学")), options(2, "")));
    }
}
