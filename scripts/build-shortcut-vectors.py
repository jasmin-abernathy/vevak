#!/usr/bin/env python3
"""Reproduce Android vectors from the six licensed, vendored Streamline SVGs."""
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
ANDROID = 'http://schemas.android.com/apk/res/android'
ET.register_namespace('android', ANDROID)
def attrs(**values):
    return {f'{{{ANDROID}}}{k}': v for k, v in values.items()}
def color(value):
    value = {'none': '#00000000', 'gray': '#808080'}.get(value, value)
    return '#' + ''.join(c * 2 for c in value[1:]) if value.startswith('#') and len(value) == 4 else value
for name in ('notes', 'list', 'clock', 'folder', 'tools', 'memo'):
    source = ET.parse(ROOT / f'third_party/streamline-ultimate-color/{name}.svg').getroot()
    vector = ET.Element('vector', attrs(width='48dp', height='48dp', viewportWidth='32', viewportHeight='32'))
    group = ET.SubElement(vector, 'group', attrs(translateX='4', translateY='4'))
    def visit(node, inherited):
        values = {**inherited, **node.attrib}
        tag = node.tag.split('}')[-1]
        if tag == 'path':
            out = attrs(pathData=values['d'], fillColor=color(values.get('fill', '#000000')))
            if 'stroke' in values:
                out.update(attrs(strokeColor=color(values['stroke']), strokeWidth=values.get('stroke-width', '1'), strokeLineCap=values.get('stroke-linecap', 'butt'), strokeLineJoin=values.get('stroke-linejoin', 'miter')))
            ET.SubElement(group, 'path', out)
        elif tag in ('svg', 'g'):
            for child in node: visit(child, values)
        else: raise ValueError(f'Unsupported SVG element: {tag}')
    visit(source, {})
    ET.indent(vector)
    output = '<!-- Streamline Ultimate Color, CC BY 4.0. See third_party/streamline-ultimate-color/NOTICE.md. -->\n' + ET.tostring(vector, encoding='unicode') + '\n'
    (ROOT / f'app/src/main/res/drawable/ic_shortcut_{name}.xml').write_text(output)
print('Streamline shortcut vectors: generated from vendored SVG sources')
