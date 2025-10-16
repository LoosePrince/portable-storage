package com.portable.storage.client.util;

import com.portable.storage.client.ClientConfig;
import java.util.*;

/**
 * 多音字词典
 * 用于处理中文多音字的拼音搜索问题
 * 从客户端配置文件动态读取多音字映射表
 */
public class PolyphoneDictionary {
    
    /**
     * 获取多音字映射表
     * 从客户端配置中动态获取
     * 
     * @return 多音字映射表
     */
    private static Map<String, String[]> getPolyphoneMap() {
        return ClientConfig.getInstance().polyphoneMap;
    }
    
    /**
     * 检查字符是否为多音字
     * 
     * @param character 要检查的字符
     * @return 是否为多音字
     */
    public static boolean isPolyphone(String character) {
        return getPolyphoneMap().containsKey(character);
    }
    
    /**
     * 获取字符的所有可能拼音
     * 
     * @param character 字符
     * @return 所有可能的拼音数组，如果不是多音字则返回null
     */
    public static String[] getPolyphonePinyins(String character) {
        return getPolyphoneMap().get(character);
    }
    
    /**
     * 生成文本的所有可能拼音组合
     * 考虑多音字的不同读音
     * 
     * @param text 要转换的文本
     * @return 可能的拼音列表
     */
    public static List<String> generateAllPossiblePinyins(String text) {
        List<String> result = new ArrayList<>();
        
        if (text == null || text.isEmpty()) {
            return result;
        }
        
        // 使用递归生成所有可能的拼音组合
        generatePinyinCombinations(text, 0, new StringBuilder(), result);
        
        return result;
    }
    
    /**
     * 递归生成拼音组合
     * 
     * @param text 原始文本
     * @param index 当前处理的字符索引
     * @param current 当前构建的拼音字符串
     * @param result 结果列表
     */
    private static void generatePinyinCombinations(String text, int index, StringBuilder current, List<String> result) {
        if (index >= text.length()) {
            // 到达文本末尾，添加当前组合到结果
            if (current.length() > 0) {
                result.add(current.toString());
            }
            return;
        }
        
        char c = text.charAt(index);
        String charStr = String.valueOf(c);
        
        // 如果不是第一个字符，添加空格分隔符
        boolean needSpace = current.length() > 0;
        
        if (isPolyphone(charStr)) {
            // 多音字，尝试所有可能的读音
            String[] pinyins = getPolyphonePinyins(charStr);
            for (String pinyin : pinyins) {
                if (needSpace) {
                    current.append(" ");
                }
                current.append(pinyin);
                generatePinyinCombinations(text, index + 1, current, result);
                // 回溯
                current.setLength(current.length() - pinyin.length() - (needSpace ? 1 : 0));
            }
        } else {
            // 非多音字，使用TinyPinyin默认转换
            try {
                String defaultPinyin = com.github.promeg.pinyinhelper.Pinyin.toPinyin(charStr, ""); // 单个字符不需要分隔符
                if (!defaultPinyin.isEmpty()) {
                    if (needSpace) {
                        current.append(" ");
                    }
                    current.append(defaultPinyin);
                    generatePinyinCombinations(text, index + 1, current, result);
                    // 回溯
                    current.setLength(current.length() - defaultPinyin.length() - (needSpace ? 1 : 0));
                } else {
                    // 无法转换的字符，跳过
                    generatePinyinCombinations(text, index + 1, current, result);
                }
            } catch (Exception ignored) {
                // 转换失败，跳过
                generatePinyinCombinations(text, index + 1, current, result);
            }
        }
    }
    
    /**
     * 获取所有已配置的多音字
     * 
     * @return 多音字集合
     */
    public static Set<String> getAllPolyphones() {
        return new HashSet<>(getPolyphoneMap().keySet());
    }
    
    /**
     * 添加新的多音字（运行时动态添加）
     * 通过客户端配置进行添加，会自动保存到配置文件
     * 
     * @param character 字符
     * @param pinyins 所有可能的拼音
     */
    public static void addPolyphone(String character, String... pinyins) {
        ClientConfig.getInstance().addPolyphone(character, pinyins);
    }
    
    /**
     * 移除多音字
     * 通过客户端配置进行移除，会自动保存到配置文件
     * 
     * @param character 字符
     */
    public static void removePolyphone(String character) {
        ClientConfig.getInstance().removePolyphone(character);
    }
}
