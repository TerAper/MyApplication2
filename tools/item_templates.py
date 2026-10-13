# Makes feature/catalog/.../types/ItemTemplates.kt and the template texts (hy, en) from the table below.
# Change the table, then run from the project folder: python3 tools/item_templates.py "$PWD"
import re, sys
ROOT = sys.argv[1]

# Field names (key: (hy, en))
F = {
 'brand': ('Բրենդ', 'Brand'),
 'print_type': ('Տպման տեսակ', 'Print type'),
 'color_mode': ('Գույն', 'Color'),
 'format': ('Ֆորմատ', 'Paper size'),
 'functions': ('Ֆունկցիաներ', 'Functions'),
 'wifi': ('Wi-Fi', 'Wi-Fi'),
 'network': ('Ցանցային (LAN)', 'Network (LAN)'),
 'duplex': ('Երկկողմանի տպում', 'Two-sided printing'),
 'ink_color': ('Գույն', 'Color'),
 'yield': ('Ռեսուրս (էջ)', 'Page yield'),
 'origin': ('Օրիգինալ / համատեղելի', 'Original / compatible'),
 'refill': ('Լիցքավորվող', 'Refillable'),
 'weight_g': ('Քաշ (գ)', 'Weight (g)'),
 'part_no': ('Կոդ (part number)', 'Part number'),
 'category': ('Տեսակ', 'Kind'),
 'size': ('Չափս', 'Size'),
 'size_num': ('Չափս', 'Size'),
 'height_size': ('Չափս (հասակ, սմ)', 'Size (height, cm)'),
 'color': ('Գույն', 'Color'),
 'material': ('Նյութ', 'Material'),
 'season': ('Սեզոն', 'Season'),
 'for_adult': ('Ում համար', 'For'),
 'for_kid': ('Ում համար', 'For'),
 'birth': ('Ծննդյան ամսաթիվ', 'Birth date'),
 'sex': ('Սեռ', 'Sex'),
 'height': ('Հասակ (սմ)', 'Height (cm)'),
 'weight': ('Քաշ (կգ)', 'Weight (kg)'),
 'shoe_kind': ('Տեսակ', 'Kind'),
 'device_kind': ('Ինչ սարք', 'Kind'),
 'model_code': ('Մոդելի կոդ', 'Model code'),
 'storage': ('Հիշողություն (ԳԲ)', 'Storage (GB)'),
 'capacity': ('Տարողություն (mAh)', 'Capacity (mAh)'),
 'quality': ('Որակ', 'Quality'),
 'year': ('Տարի', 'Year'),
 'plate': ('Համարանիշ', 'Plate number'),
 'fuel': ('Վառելիք', 'Fuel'),
 'engine': ('Շարժիչ (լ)', 'Engine (l)'),
 'mileage': ('Վազք (կմ)', 'Mileage (km)'),
 'tire_size': ('Չափս (օր.՝ 205/55 R16)', 'Size (e.g. 205/55 R16)'),
 'viscosity': ('Մածուցիկություն (օր.՝ 5W-30)', 'Viscosity (e.g. 5W-30)'),
 'volume_l': ('Ծավալ (լ)', 'Volume (l)'),
 'filter_kind': ('Ինչ ֆիլտր', 'Kind'),
 'pc_kind': ('Ինչ համակարգիչ', 'Kind'),
 'cpu': ('Պրոցեսոր', 'Processor'),
 'ram_gb': ('Օպերատիվ հիշողություն (ԳԲ)', 'RAM (GB)'),
 'disk_kind': ('Տեսակ', 'Kind'),
 'disk_gb': ('Ծավալ (ԳԲ)', 'Size (GB)'),
 'watts': ('Հզորություն (Վտ)', 'Power (W)'),
 'appliance_kind': ('Ինչ տեխնիկա', 'Kind'),
}
# Choice options (key: (hy list, en list))
O = {
 'print_type': (['Լազերային','Թանաքային','Մատրիցային','Ջերմային'], ['Laser','Inkjet','Dot matrix','Thermal']),
 'color_mode': (['Սև/սպիտակ','Գունավոր'], ['Black/white','Color']),
 'format': (['A4','A3','A5'], ['A4','A3','A5']),
 'functions': (['Տպիչ','ԲՖՍ (տպիչ, սկաներ, պատճենահանող)','Ֆաքսով ԲՖՍ'], ['Printer','Multifunction (print, scan, copy)','Multifunction with fax']),
 'ink_color': (['Սև','Կապույտ (Cyan)','Մանուշակագույն (Magenta)','Դեղին (Yellow)'], ['Black','Cyan','Magenta','Yellow']),
 'origin': (['Օրիգինալ','Համատեղելի'], ['Original','Compatible']),
 'clothing_cat': (['Շապիկ','Վերնաշապիկ','Տաբատ','Ջինս','Զգեստ','Կիսաշրջազգեստ','Բաճկոն','Սվիտեր','Կոստյում'], ['T-shirt','Shirt','Pants','Jeans','Dress','Skirt','Jacket','Sweater','Suit']),
 'size': (['XS','S','M','L','XL','XXL','3XL'], ['XS','S','M','L','XL','XXL','3XL']),
 'season': (['Ամառ','Ձմեռ','Գարուն/աշուն','Ամբողջ տարի'], ['Summer','Winter','Spring/autumn','All year']),
 'for_adult': (['Տղամարդ','Կին'], ['Men','Women']),
 'for_kid': (['Տղա','Աղջիկ'], ['Boy','Girl']),
 'sex': (['Տղա','Աղջիկ'], ['Boy','Girl']),
 'shoe_kind': (['Սպորտային','Կոշիկ','Երկարաճիտ','Սանդալ','Հողաթափ'], ['Sneakers','Shoes','Boots','Sandals','Slippers']),
 'device_kind': (['Հեռախոս','Պլանշետ','Նոութբուք','Ժամացույց','Այլ'], ['Phone','Tablet','Laptop','Watch','Other']),
 'quality': (['Օրիգինալ','OLED','Incell','LCD պատճեն'], ['Original','OLED','Incell','LCD copy']),
 'fuel': (['Բենզին','Դիզել','Գազ','Հիբրիդ','Էլեկտրական'], ['Petrol','Diesel','Gas','Hybrid','Electric']),
 'filter_kind': (['Յուղի','Օդի','Սրահի','Վառելիքի'], ['Oil','Air','Cabin','Fuel']),
 'pc_kind': (['Նոութբուք','Սեղանի','Մոնոբլոկ','Սերվեր'], ['Laptop','Desktop','All-in-one','Server']),
 'disk_kind': (['SSD','HDD','NVMe'], ['SSD','HDD','NVMe']),
 'appliance_kind': (['Լվացքի մեքենա','Սառնարան','Աման լվացող','Ջեռոց','Օդորակիչ','Ջրատաքացուցիչ','Միկրոալիքային'], ['Washing machine','Fridge','Dishwasher','Oven','Air conditioner','Water heater','Microwave']),
}
# Type names
T = {
 'printer': ('Տպիչ','Printer'), 'cartridge': ('Քարտրիջ','Cartridge'), 'chip': ('Չիպ','Chip'),
 'drum': ('Թմբուկ (դրամ)','Drum unit'), 'toner': ('Տոներ (փոշի)','Toner powder'), 'printer_part': ('Պահեստամաս','Spare part'),
 'clothing': ('Հագուստ','Clothing'),
 'child': ('Երեխա','Child'), 'kid_shoe': ('Կոշիկ','Shoes'), 'kid_pants': ('Տաբատ','Pants'), 'kid_top': ('Վերնաշապիկ','Top'), 'kid_jacket': ('Բաճկոն','Jacket'),
 'shoes': ('Կոշիկ','Shoes'),
 'device': ('Սարք','Device'), 'battery': ('Մարտկոց','Battery'), 'screen': ('Էկրան','Screen'), 'port': ('Լիցքավորման մուտք','Charging port'), 'back': ('Հետևի կափարիչ','Back cover'), 'camera': ('Տեսախցիկ','Camera'),
 'car': ('Մեքենա','Car'), 'car_part': ('Պահեստամաս','Spare part'), 'tire': ('Անվադող','Tire'), 'oil': ('Յուղ','Oil'), 'filter': ('Ֆիլտր','Filter'),
 'computer': ('Համակարգիչ','Computer'), 'ram': ('Օպերատիվ հիշողություն','RAM'), 'disk': ('Կուտակիչ','Storage drive'), 'psu': ('Սնուցման բլոկ','Power supply'),
 'appliance': ('Տեխնիկա','Appliance'), 'appliance_part': ('Պահեստամաս','Spare part'),
}
# Template names
N = {
 'printers': ('Տպիչների սպասարկում','Printer service'),
 'clothes': ('Հագուստի խանութ','Clothes shop'),
 'kids': ('Մանկական հագուստ և կոշիկ','Children\'s clothes and shoes'),
 'shoes': ('Կոշիկի խանութ','Shoe shop'),
 'electronics': ('Հեռախոսների և տեխնիկայի վերանորոգում','Phone and electronics repair'),
 'cars': ('Ավտոսպասարկում','Car service'),
 'computers': ('Համակարգիչների սպասարկում','Computer service'),
 'appliances': ('Կենցաղային տեխնիկայի վերանորոգում','Home appliance repair'),
}
def fld(key, kind='TEXT', opts=None, title=False): return (key, kind, opts, title)
B = fld('brand', title=True)
# (template key, [(type key, parent, fields, hasSerial, perClient, hasPhotos)])
TEMPLATES = [
 ('printers', [
   ('printer', None, [B, fld('print_type','CHOICE','print_type'), fld('color_mode','CHOICE','color_mode'), fld('format','CHOICE','format'),
                      fld('functions','CHOICE','functions'), fld('wifi','YES_NO'), fld('network','YES_NO'), fld('duplex','YES_NO')], True, False, True),
   ('cartridge', 'printer', [B, fld('ink_color','CHOICE','ink_color'), fld('yield','NUMBER'), fld('origin','CHOICE','origin'), fld('refill','YES_NO')], False, False, True),
   ('chip', 'cartridge', [B, fld('yield','NUMBER')], False, False, False),
   ('toner', 'cartridge', [B, fld('ink_color','CHOICE','ink_color'), fld('weight_g','NUMBER')], False, False, False),
   ('drum', 'printer', [B, fld('yield','NUMBER'), fld('origin','CHOICE','origin')], False, False, True),
   ('printer_part', 'printer', [B, fld('part_no'), fld('origin','CHOICE','origin')], False, False, True),
 ]),
 ('clothes', [
   ('clothing', None, [B, fld('category','CHOICE','clothing_cat'), fld('size','CHOICE','size'), fld('color'), fld('material'),
                       fld('season','CHOICE','season'), fld('for_adult','CHOICE','for_adult')], False, False, True),
 ]),
 ('kids', [
   ('child', None, [fld('birth','DATE'), fld('sex','CHOICE','sex'), fld('height','NUMBER'), fld('weight','NUMBER')], False, True, True),
   ('kid_shoe', 'child', [B, fld('size_num','NUMBER'), fld('for_kid','CHOICE','for_kid'), fld('shoe_kind','CHOICE','shoe_kind'), fld('season','CHOICE','season'), fld('color')], False, False, True),
   ('kid_pants', 'child', [B, fld('height_size','NUMBER'), fld('for_kid','CHOICE','for_kid'), fld('season','CHOICE','season'), fld('color')], False, False, True),
   ('kid_top', 'child', [B, fld('height_size','NUMBER'), fld('for_kid','CHOICE','for_kid'), fld('season','CHOICE','season'), fld('color')], False, False, True),
   ('kid_jacket', 'child', [B, fld('height_size','NUMBER'), fld('for_kid','CHOICE','for_kid'), fld('season','CHOICE','season'), fld('color')], False, False, True),
 ]),
 ('shoes', [
   ('shoes', None, [B, fld('shoe_kind','CHOICE','shoe_kind'), fld('size_num','NUMBER'), fld('for_adult','CHOICE','for_adult'),
                    fld('color'), fld('material'), fld('season','CHOICE','season')], False, False, True),
 ]),
 ('electronics', [
   ('device', None, [B, fld('device_kind','CHOICE','device_kind'), fld('model_code'), fld('color'), fld('storage','NUMBER')], True, False, True),
   ('screen', 'device', [B, fld('quality','CHOICE','quality'), fld('color')], False, False, True),
   ('battery', 'device', [B, fld('capacity','NUMBER'), fld('origin','CHOICE','origin')], False, False, True),
   ('port', 'device', [B, fld('origin','CHOICE','origin')], False, False, True),
   ('back', 'device', [B, fld('color')], False, False, True),
   ('camera', 'device', [B, fld('origin','CHOICE','origin')], False, False, True),
 ]),
 ('cars', [
   ('car', None, [B, fld('year','NUMBER'), fld('plate'), fld('fuel','CHOICE','fuel'), fld('engine','NUMBER'), fld('mileage','NUMBER'), fld('color')], True, False, True),
   ('car_part', 'car', [B, fld('part_no'), fld('origin','CHOICE','origin')], False, False, True),
   ('tire', 'car', [B, fld('tire_size'), fld('season','CHOICE','season')], False, False, True),
   ('oil', 'car', [B, fld('viscosity'), fld('volume_l','NUMBER')], False, False, False),
   ('filter', 'car', [B, fld('filter_kind','CHOICE','filter_kind'), fld('part_no')], False, False, False),
 ]),
 ('computers', [
   ('computer', None, [B, fld('pc_kind','CHOICE','pc_kind'), fld('cpu'), fld('ram_gb','NUMBER'), fld('storage','NUMBER')], True, False, True),
   ('ram', 'computer', [B, fld('ram_gb','NUMBER')], False, False, False),
   ('disk', 'computer', [B, fld('disk_kind','CHOICE','disk_kind'), fld('disk_gb','NUMBER')], True, False, False),
   ('psu', 'computer', [B, fld('watts','NUMBER')], False, False, False),
 ]),
 ('appliances', [
   ('appliance', None, [B, fld('appliance_kind','CHOICE','appliance_kind'), fld('model_code')], True, False, True),
   ('appliance_part', 'appliance', [B, fld('part_no'), fld('origin','CHOICE','origin')], False, False, True),
 ]),
]
# Two parts named "Spare part" in different templates must stay apart: types are matched by name,
# so the car and appliance ones get their own names.
T['car_part'] = ('Ավտոպահեստամաս', 'Car part')
T['appliance_part'] = ('Տեխնիկայի պահեստամաս', 'Appliance part')
T['printer_part'] = ('Տպիչի պահեստամաս', 'Printer part')

def esc(v): return v.replace("'", "\\'")
def xml(lang):
    i = 0 if lang == 'hy' else 1
    out = []
    for k,v in N.items(): out.append(f'    <string name="feature_catalog_tpl_{k}">{esc(v[i])}</string>')
    for k,v in T.items(): out.append(f'    <string name="feature_catalog_tpl_t_{k}">{esc(v[i])}</string>')
    for k,v in F.items(): out.append(f'    <string name="feature_catalog_tpl_f_{k}">{esc(v[i])}</string>')
    for k,v in O.items(): out.append(f'    <string name="feature_catalog_tpl_o_{k}">{esc(chr(92)+"n").join(esc(x) for x in v[i])}</string>')
    return '\n'.join(out)

for lang, path in [('hy','values/strings.xml'),('en','values-en/strings.xml')]:
    p = f'{ROOT}/feature/catalog/src/main/res/{path}'
    s = open(p).read()
    s = re.sub(r'\n\s*<!-- Templates -->.*?(?=\n</resources>)', '', s, flags=re.S)
    s = re.sub(r'\n\s*<string name="feature_catalog_tpl_[^"]*">.*?</string>', '', s)
    s = s.replace('</resources>', '    <!-- Templates (made by the template table) -->\n' + xml(lang) + '\n</resources>')
    open(p,'w').write(s)

kt = ['''package com.teraper.printmaster.feature.catalog.types

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.teraper.printmaster.core.model.FieldDraft
import com.teraper.printmaster.core.model.FieldKind
import com.teraper.printmaster.core.model.ItemTemplate
import com.teraper.printmaster.core.model.TypeTemplate
import com.teraper.printmaster.feature.catalog.R

/*
 * Ready sets of types for common businesses, in the app's language. Parents come before their parts.
 * Types are matched by name when a template is added again or sync makes them on another phone,
 * so parts of different templates have their own names ("Car part", "Printer part").
 */

@Composable
private fun text(key: Int) = stringResource(key)

@Composable
private fun field(name: Int, kind: FieldKind = FieldKind.TEXT, options: Int? = null, inTitle: Boolean = false) =
    FieldDraft(name = text(name), kind = kind, options = options?.let { text(it) }.orEmpty(), inTitle = inTitle)

@Composable
internal fun itemTemplates(): List<ItemTemplate> = listOf(''']
for tkey, types in TEMPLATES:
    kt.append(f'''    ItemTemplate(
        key = "{tkey}",
        name = text(R.string.feature_catalog_tpl_{tkey}),
        types = listOf(''')
    for (ty, parent, fields, serial, perClient, photos) in types:
        fl = []
        for (fk, kind, opts, title) in fields:
            args = [f'R.string.feature_catalog_tpl_f_{fk}']
            if kind != 'TEXT': args.append(f'FieldKind.{kind}')
            if opts: args.append(f'options = R.string.feature_catalog_tpl_o_{opts}')
            if title: args.append('inTitle = true')
            fl.append('field(' + ', '.join(args) + ')')
        parent_arg = f'parentName = text(R.string.feature_catalog_tpl_t_{parent})' if parent else 'parentName = null'
        kt.append(f'''            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_{ty}),
                {parent_arg},
                fields = listOf(
''' + ''.join(f'                    {x},\n' for x in fl) + f'''                ),
                hasSerial = {str(serial).lower()},
                perClient = {str(perClient).lower()},
                hasPhotos = {str(photos).lower()},
            ),''')
    kt.append('''        ),
    ),''')
kt.append(')\n')
open(f'{ROOT}/feature/catalog/src/main/kotlin/com/teraper/printmaster/feature/catalog/types/ItemTemplates.kt','w').write('\n'.join(kt))
print('ok', len(TEMPLATES), 'templates', sum(len(t[1]) for t in TEMPLATES), 'types')
