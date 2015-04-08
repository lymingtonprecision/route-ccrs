select distinct
  decode(
    bom_type_db,
    'M', 'manufactured',
    'F', 'repair',
    'P', 'purchased',
    ''
  ) type,
  eng_chg_level revision,
  structure_alternative alternative,
  ip.purch_leadtime lead_time,
  null best_end_date
from ifsinfo.active_structure_routings asr
join ifsapp.inventory_part ip
  on asr.contract = ip.contract
  and asr.part_no = ip.part_no
where asr.contract = 'LPE'
  and asr.part_no = :part_no
