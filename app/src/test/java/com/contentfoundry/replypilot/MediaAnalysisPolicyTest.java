package com.contentfoundry.replypilot;

import org.junit.Test;
import static org.junit.Assert.*;

public class MediaAnalysisPolicyTest {
    @Test public void acceptsTentativeReviewOnlyInterpretation(){
        var result=MediaAnalysisPolicy.validate("Burnt toast","Possibly sharing a kitchen mishap","medium","","Five-star chef strikes again 😂","reply_needed");
        assertEquals("Five-star chef strikes again 😂",result.suggestion());
    }
    @Test public void planningSuggestionsBecomeOwnerHandoffs(){
        var result=MediaAnalysisPolicy.validate("Invitation","Possibly making plans","high","","I'll be there","reply_needed");
        assertEquals("plans_need_input",result.reason());assertEquals("",result.suggestion());
    }
    @Test public void rejectsInvalidAndContradictoryModelShapes(){
        assertThrows(IllegalStateException.class,()->MediaAnalysisPolicy.validate(null,"","low","","","needs_review"));
        assertThrows(IllegalStateException.class,()->MediaAnalysisPolicy.validate("","","certain","","","needs_review"));
        assertThrows(IllegalStateException.class,()->MediaAnalysisPolicy.validate("","","low","","send this","needs_review"));
        assertThrows(IllegalStateException.class,()->MediaAnalysisPolicy.validate("","","low","","","reply_needed"));
        assertThrows(IllegalStateException.class,()->MediaAnalysisPolicy.validate("x".repeat(601),"","low","","","needs_review"));
    }
    @Test public void rejectsTutorialShapedReplyWithoutTruncatingIt(){
        var result=MediaAnalysisPolicy.validate("Screenshot","Possibly asking for help","medium","","1. First\n2. Second\n3. Third","reply_needed");
        assertEquals("needs_review",result.reason());assertEquals("",result.suggestion());
    }
    @Test public void withheldSuggestionCanStillExplainMedia(){
        var result=MediaAnalysisPolicy.validate("Event invitation","May be an invitation to attend","medium","Intent is uncertain.","","plans_need_input");
        assertEquals("Event invitation",result.summary());assertEquals("",result.suggestion());
    }
}
