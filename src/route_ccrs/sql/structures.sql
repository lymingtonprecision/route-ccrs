select distinct
  decode(
    asr.bom_type_db,
    'M', 'manufactured',
    'F', 'repair',
    'P', 'purchased',
    ''
  ) type,
  asr.eng_chg_level revision,
  asr.structure_alternative alternative,
  psa.alternative_description description,
  ip.purch_leadtime lead_time,
  null best_end_date
from ifsinfo.active_structure_routings asr
join ifsapp.prod_struct_alternate psa
  on asr.contract = psa.contract
  and asr.part_no = psa.part_no
  and asr.bom_type_db = psa.bom_type_db
  and asr.eng_chg_level = psa.eng_chg_level
  and asr.structure_alternative = psa.alternative_no
join ifsapp.inventory_part ip
  on asr.contract = ip.contract
  and asr.part_no = ip.part_no
where asr.contract = 'LPE'
  and asr.part_no = :part_no
