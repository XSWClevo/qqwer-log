package cn.mw.loganalysis.vector.service;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 自定义 YAML Representer：
 * 1. 对可能被误判为数字/布尔的字符串强制加双引号
 * 2. 对 password 字段的值始终加双引号（Vector ClickHouse sink 要求）
 */
public class PasswordQuotingRepresenter extends Representer {

    public PasswordQuotingRepresenter(DumperOptions options) {
        super(options);
    }

    @Override
    protected Node representScalar(Tag tag, String value, DumperOptions.ScalarStyle style) {
        if (tag == Tag.STR && style == DumperOptions.ScalarStyle.PLAIN && looksLikeNonString(value)) {
            style = DumperOptions.ScalarStyle.DOUBLE_QUOTED;
        }
        return super.representScalar(tag, value, style);
    }

    @Override
    protected Node representMapping(Tag tag, Map<?, ?> mapping, DumperOptions.FlowStyle flowStyle) {
        MappingNode node = (MappingNode) super.representMapping(tag, mapping, flowStyle);

        List<NodeTuple> newTuples = new ArrayList<>();
        for (NodeTuple tuple : node.getValue()) {
            Node keyNode = tuple.getKeyNode();
            Node valueNode = tuple.getValueNode();

            if (keyNode instanceof ScalarNode scalarKey
                    && "password".equals(scalarKey.getValue())
                    && valueNode instanceof ScalarNode scalarValue
                    && scalarValue.getTag().equals(Tag.STR)) {
                // password 值始终使用双引号
                ScalarNode quotedValue = new ScalarNode(
                        scalarValue.getTag(),
                        scalarValue.getValue(),
                        scalarValue.getStartMark(),
                        scalarValue.getEndMark(),
                        DumperOptions.ScalarStyle.DOUBLE_QUOTED
                );
                newTuples.add(new NodeTuple(keyNode, quotedValue));
            } else {
                newTuples.add(tuple);
            }
        }
        node.setValue(newTuples);
        return node;
    }

    private boolean looksLikeNonString(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        if (value.matches("^-?\\d+(\\.\\d+)?$")) {
            return true;
        }
        String lower = value.toLowerCase();
        return "true".equals(lower) || "false".equals(lower)
                || "yes".equals(lower) || "no".equals(lower)
                || "on".equals(lower) || "off".equals(lower);
    }
}
