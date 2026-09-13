#!/usr/bin/env python3
"""Convert the vendored Papirus subset; no SVG library is shipped in the app."""
from pathlib import Path
import xml.etree.ElementTree as ET
from xml.sax.saxutils import quoteattr

ROOT = Path(__file__).resolve().parents[1]
SOURCES = {
    'notes': 'accessories-text-editor', 'list': 'checklist',
    'clock': 'preferences-system-time', 'folder': 'system-file-manager',
    'tools': 'applications-utilities', 'memo': 'accessories-dictionary',
}


def color(value):
    if value == 'none':
        return '#00000000'
    assert value.startswith('#'), value
    return '#' + ''.join(c * 2 for c in value[1:]) if len(value) == 4 else value


def convert(element):
    attrs = dict(element.attrib)
    style = dict(part.strip().split(':', 1) for part in attrs.pop('style', '').split(';') if part.strip())
    attrs.update(style)
    tag = element.tag.rsplit('}', 1)[-1]
    transform = attrs.pop('transform', None)
    if tag == 'path':
        path = attrs.pop('d')
    elif tag == 'circle':
        x, y, r = (float(attrs.pop(k)) for k in ('cx', 'cy', 'r'))
        path = f'M{x-r},{y}a{r},{r} 0 1,0 {2*r},0a{r},{r} 0 1,0 {-2*r},0Z'
    elif tag == 'rect':
        x, y, w, h = (float(attrs.pop(k)) for k in ('x', 'y', 'width', 'height'))
        rx = float(attrs.pop('rx', 0)); ry = float(attrs.pop('ry', rx))
        if rx and ry:
            path = (f'M{x+rx},{y}H{x+w-rx}a{rx},{ry} 0 0,1 {rx},{ry}'
                    f'V{y+h-ry}a{rx},{ry} 0 0,1 {-rx},{ry}H{x+rx}'
                    f'a{rx},{ry} 0 0,1 {-rx},{-ry}V{y+ry}a{rx},{ry} 0 0,1 {rx},{-ry}Z')
        else:
            path = f'M{x},{y}h{w}v{h}h{-w}Z'
    else:
        raise ValueError(f'Unsupported SVG element: {tag}')
    output = {'pathData': path, 'fillColor': color(attrs.pop('fill', '#000000'))}
    if 'opacity' in attrs:
        output['fillAlpha'] = attrs.pop('opacity')
    for source, target in [('stroke', 'strokeColor'), ('stroke-width', 'strokeWidth'),
                           ('stroke-linecap', 'strokeLineCap'), ('stroke-linejoin', 'strokeLineJoin')]:
        if source in attrs:
            value = attrs.pop(source)
            output[target] = color(value) if source == 'stroke' else value
    # Editor metadata has no effect on these paths (no text or gradient nodes).
    for key in ('font-variation-settings', 'inline-size', 'stop-color'):
        attrs.pop(key, None)
    assert not attrs, attrs
    node = '<path ' + ' '.join(f'android:{k}={quoteattr(str(v))}' for k, v in output.items()) + '/>'
    if transform:
        assert transform == 'matrix(0,-1,-1,0,0,0)', transform
        node = '<group android:rotation="-90" android:scaleY="-1">' + node + '</group>'
    return node


for name, source in SOURCES.items():
    svg = ET.parse(ROOT / f'third_party/papirus/{source}.svg').getroot()
    assert svg.attrib['width'] == svg.attrib['height'] == '48'
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             '<!-- Papirus contributors, GPL-3.0. Adapted 2026-09-13; see docs/discreet-shortcut-icons.md. -->',
             '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="48dp" android:height="48dp" android:viewportWidth="48" android:viewportHeight="48">']
    lines.extend('    ' + convert(element) for element in svg)
    lines.extend(['</vector>', ''])
    (ROOT / f'app/src/main/res/drawable/ic_shortcut_{name}.xml').write_text('\n'.join(lines))
