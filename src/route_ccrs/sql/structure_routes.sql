select distinct
  decode(
    bom_type_db,
    'M', 'manufactured',
    'F', 'repair',
    'P', 'purchased',
    ''
  ) type,
  routing_revision revision,
  routing_alternative alternative
from ifsinfo.active_structure_routings asr
where asr.contract = 'LPE'
  and asr.part_no = :part_no
  and asr.bom_type_db = decode(
    :bom_type,
    'manufactured', 'M',
    'repair', 'F',
    'purchased', 'P',
    ''
  )
  and asr.eng_chg_level = :revision
  and asr.structure_alternative = :alternative
  and asr.routing_revision is not null
  and asr.routing_alternative is not null
