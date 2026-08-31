package com.cherryoj.problemservice.persistence;

import com.cherryoj.problemservice.persistence.PublicProblemRows.LanguageRow;
import com.cherryoj.problemservice.persistence.PublicProblemRows.ProblemRow;
import com.cherryoj.problemservice.persistence.PublicProblemRows.SampleRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PublicProblemMapper {

    List<ProblemRow> findPublicProblems(PublicProblemSearch search);

    ProblemRow findPublicProblemBySlug(@Param("slug") String slug);

    List<SampleRow> findSamples(@Param("versionId") String versionId);

    List<LanguageRow> findLanguages(@Param("versionIds") List<String> versionIds);
}
