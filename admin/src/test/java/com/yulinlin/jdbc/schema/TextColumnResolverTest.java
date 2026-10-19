package com.yulinlin.jdbc.schema;

import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.TextTypeEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextColumnResolverTest {
    @Test void resolvesLengthLargeTextAndDescription() throws Exception {
        var title = definition("title");
        assertThat(title.type()).isEqualTo(TextTypeEnum.varchar);
        assertThat(title.length()).isEqualTo(80);
        assertThat(title.explicitLength()).isTrue();
        assertThat(title.description()).isEqualTo("Video title");

        var content = definition("content");
        assertThat(content.type()).isEqualTo(TextTypeEnum.text);
        assertThat(content.length()).isZero();
    }

    @Test void rejectsInvalidTextOptions() throws Exception {
        assertThatThrownBy(() -> definition("number"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("text-encoded fields");
        assertThatThrownBy(() -> definition("textWithLength"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cannot be used");
        assertThatThrownBy(() -> definition("negative"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("zero or positive");
    }

    private static TextColumnResolver.Definition definition(String name) throws Exception {
        var field = Model.class.getDeclaredField(name);
        return TextColumnResolver.resolve(field, field.getAnnotation(JoinField.class));
    }

    static class Model {
        @JoinField(textLength = 80, description = "Video title") String title;
        @JoinField(textType = TextTypeEnum.text, description = "Full content") String content;
        @JoinField(textLength = 10) Integer number;
        @JoinField(textType = TextTypeEnum.text, textLength = 10) String textWithLength;
        @JoinField(textLength = -1) String negative;
    }
}
