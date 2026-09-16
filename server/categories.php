<?php
require_once __DIR__ . '/security.php';

$stmt = $db->query("SELECT * FROM categories ORDER BY id ASC");
$categories = $stmt->fetchAll(PDO::FETCH_ASSOC);

$hasAll = false;
foreach ($categories as &$cat) {
    if (strcasecmp(trim($cat['name']), 'All') === 0 || strcasecmp(trim($cat['name']), 'All Channels') === 0) {
        $cat['name'] = 'All';
        $hasAll = true;
    }
}
unset($cat);

if (!$hasAll) {
    $id1Used = false;
    foreach ($categories as $cat) {
        if ((int)$cat['id'] === 1) {
            $id1Used = true;
            break;
        }
    }
    $allId = $id1Used ? 0 : 1;
    array_unshift($categories, [
        'id' => $allId,
        'name' => 'All',
        'icon' => 'ic_tv'
    ]);
} else {
    for ($i = 0; $i < count($categories); $i++) {
        if (strcasecmp(trim($categories[$i]['name']), 'All') === 0) {
            if ($i !== 0) {
                $allItem = array_splice($categories, $i, 1);
                array_unshift($categories, $allItem[0]);
            }
            break;
        }
    }
}

send_secure_response([
    "status" => "success",
    "timestamp" => time(),
    "categories" => $categories
]);
