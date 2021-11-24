package com.github.zjb;

import com.github.zjb.util.EnumUtil;
import com.google.common.collect.Lists;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.jvm.annotation.JvmAnnotationArrayValue;
import com.intellij.lang.jvm.annotation.JvmAnnotationAttributeValue;
import com.intellij.lang.jvm.annotation.JvmAnnotationConstantValue;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLFileType;
import org.jetbrains.yaml.YAMLLanguage;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLPsiElement;
import org.jetbrains.yaml.psi.impl.YAMLPlainTextImpl;

import java.util.*;

/**
 * @author zhaojingbo(zjbhnay @ 163.com)
 * 2020/12/14
 */

public class GotoYmlFile implements GotoDeclarationHandler {

    public static final String DEFAULT_SPLIT = ":";


    @Nullable
    @Override
    public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
        if (sourceElement instanceof PsiJavaToken) {
            return javaGoToYml(sourceElement);
        }
        if (sourceElement.getLanguage().is(YAMLLanguage.INSTANCE)) {
            return ymlToJava(sourceElement);
        }
        return PsiElement.EMPTY_ARRAY;
    }

    private PsiElement[] ymlToJava(@NotNull PsiElement sourceElement) {
        String configFullName = YAMLUtil.getConfigFullName(PsiTreeUtil.getParentOfType(sourceElement, YAMLPsiElement.class));
        Pair<PsiElement, String> value = YAMLUtil.getValue((YAMLFile) sourceElement.getContainingFile(), configFullName.split("\\."));
        /*//判断yaml对象的值是否是空值
        if (Objects.isNull(value) || !(value.getFirst() instanceof YAMLPlainTextImpl)) {
            return PsiElement.EMPTY_ARRAY;
        }*/
        Project project = sourceElement.getProject();
        Collection<VirtualFile> files = FileTypeIndex.getFiles(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project));
        if (CollectionUtils.isEmpty(files)) {
            return PsiElement.EMPTY_ARRAY;
        }
        ArrayList<PsiElement> result = Lists.newArrayList();
//        List<VirtualFile> collect = files.stream().filter(file -> file.getName().equals("ConfigApplication.java")).collect(Collectors.toList());
        for (VirtualFile file : files) {
            Collection<PsiAnnotation> childrenOfType = PsiTreeUtil.findChildrenOfType(PsiManager.getInstance(project).findFile(file), PsiAnnotation.class);
            if (CollectionUtils.isEmpty(childrenOfType)) {
                continue;
            }

            for (PsiAnnotation psiAnnotation : childrenOfType) {
                if (!EnumUtil.containAnnotation(psiAnnotation)) {
                    continue;
                }
                PsiNameValuePair[] attributes1 = psiAnnotation.getParameterList().getAttributes();
                for (PsiNameValuePair psiNameValuePair : attributes1) {
                    JvmAnnotationAttributeValue attributeValue = psiNameValuePair.getAttributeValue();
                    if (checkEquals(configFullName, attributeValue)) {
                        result.add(psiAnnotation);
                        //有一个相等就不匹配其它的了
                        break;
                    }
                }
            }
        }
        return result.toArray(new PsiElement[result.size()]);
    }

    private Boolean checkEquals(String configFullName, JvmAnnotationAttributeValue constantValue) {
        if (constantValue instanceof JvmAnnotationConstantValue) {
            String literalValue = ((JvmAnnotationConstantValue) constantValue).getConstantValue().toString();
            /*if (!literalValue.startsWith("$")){
                return false;
            }*/
            String valueKey = getValueKey(literalValue);
            return Objects.equals(valueKey, configFullName);
        }
        if (constantValue instanceof JvmAnnotationArrayValue) {
            JvmAnnotationArrayValue attributeValue1 = (JvmAnnotationArrayValue) constantValue;
            List<JvmAnnotationAttributeValue> annotationArrayValues = attributeValue1.getValues();
            for (JvmAnnotationAttributeValue value : annotationArrayValues) {
                if (checkEquals(configFullName, value)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getValueKey(String literalValue, Boolean isSpecial) {
        String valueKey = literalValue;
        // 去除多余的引号 默认格式:""a.b.c""
        if (literalValue.contains("\"") && isSpecial) {
            valueKey = literalValue.substring(1, literalValue.length() - 1);
        }
        return getValueKey(valueKey);
    }

    private String getValueKey(String literalValue) {
        String valueKey = literalValue;
        if (literalValue.contains("${")) {
            valueKey = literalValue.substring(literalValue.indexOf("${") + 2, literalValue.indexOf("}"));
        }
        //带有默认值的注解的解析，比如@Value("${a.b.c:123}")
        if (valueKey.contains(DEFAULT_SPLIT)) {
            valueKey = valueKey.split(DEFAULT_SPLIT)[0];
        }
        return valueKey;
    }


    private PsiElement[] javaGoToYml(@NotNull PsiElement sourceElement) {
        IElementType tokenType = ((PsiJavaToken) sourceElement).getTokenType();
        if (tokenType != JavaTokenType.STRING_LITERAL) {
            return PsiElement.EMPTY_ARRAY;
        }

        PsiAnnotation psiAnnotation = PsiTreeUtil.getParentOfType(sourceElement, PsiAnnotation.class);
        if (Objects.isNull(psiAnnotation)) {
            return PsiElement.EMPTY_ARRAY;
        }
        if (!EnumUtil.containAnnotation(psiAnnotation)) {
            return PsiElement.EMPTY_ARRAY;
        }
        String key = sourceElement.getText();
        /*if (!key.contains("$")){
            return PsiElement.EMPTY_ARRAY;
        }*/
        key = getValueKey(key, EnumUtil.isSpecial(psiAnnotation));
        Project project = sourceElement.getProject();
        Collection<VirtualFile> files = FileTypeIndex.getFiles(YAMLFileType.YML, GlobalSearchScope.projectScope(project));
        if (CollectionUtils.isEmpty(files)) {
            return PsiElement.EMPTY_ARRAY;
        }

        List<PsiElement> result = new ArrayList<>(files.size());
        for (VirtualFile file : files) {
            Pair<PsiElement, String> value = YAMLUtil.getValue((YAMLFile) PsiManager.getInstance(sourceElement.getProject()).findFile(file), key.split("\\."));
            if (Objects.nonNull(value)) {
                PsiElement first = value.getFirst();
                result.add(first);
            }
        }
        return result.toArray(new PsiElement[result.size()]);
    }


}
