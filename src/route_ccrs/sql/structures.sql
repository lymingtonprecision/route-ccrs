select distinct
  decode(
    bom_type_db,
    'M', 'manufactured',
    'F', 'repair',
    'P', 'purchased',
    ''
  ) type,
  eng_chg_level revision,
  alternative_no alternative,
  ip.purch_leadtime lead_time,
  null best_end_date
from ifsinfo.valid_product_structures vps
join ifsapp.inventory_part ip
  on vps.contract = ip.contract
  and vps.part_no = ip.part_no
where vps.contract = 'LPE'
  and vps.part_no = :part_no
