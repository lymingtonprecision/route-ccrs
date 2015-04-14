select distinct
  decode(
    vps.bom_type_db,
    'M', 'manufactured',
    'F', 'repair',
    'P', 'purchased',
    ''
  ) type,
  vps.eng_chg_level revision,
  vps.alternative_no alternative,
  psa.alternative_description description,
  ip.purch_leadtime lead_time,
  null best_end_date
from ifsinfo.valid_product_structures vps
join ifsapp.prod_struct_alternate psa
  on vps.contract = psa.contract
  and vps.part_no = psa.part_no
  and vps.bom_type_db = psa.bom_type_db
  and vps.eng_chg_level = psa.eng_chg_level
  and vps.alternative_no = psa.alternative_no
join ifsapp.inventory_part ip
  on vps.contract = ip.contract
  and vps.part_no = ip.part_no
where vps.contract = 'LPE'
  and vps.part_no = :part_no
