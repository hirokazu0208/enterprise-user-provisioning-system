const API_BASE = "http://localhost:8080";

document.addEventListener("DOMContentLoaded", () => {
    loadUsers();
    loadAdOus();
});

async function loadUsers() {
    try {
        const response = await fetch(`${API_BASE}/api/users`);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const users = await response.json();
        renderUsers(users);
    } catch (error) {
        console.error(error);
        const tbody = document.getElementById("userTableBody");
        if (tbody) tbody.innerHTML = '<tr><td colspan="9">取得失敗</td></tr>';
    }
}

function renderUsers(users) {
    const tbody = document.getElementById("userTableBody");
    if (!tbody) return;
    tbody.innerHTML = "";

    users.forEach((user, index) => {
        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td>${index + 1}</td>
            <td>${escapeHtml(user["年度"] || "")}</td>
            <td>${escapeHtml(user["所属名称"] || "")}</td>
            <td>${escapeHtml(user["職員番号ログインID"] || "")}</td>
            <td>${escapeHtml(user["姓"] || "")}</td>
            <td>${escapeHtml(user["名"] || "")}</td>
            <td>${escapeHtml(user["担当名称"] || "")}</td>
            <td><button type="button" class="btn btn-sm btn-primary">編集</button></td>
            <td><button type="button" class="btn btn-sm btn-danger">削除</button></td>`;
        const buttons = tr.querySelectorAll("button");
        buttons[0].addEventListener("click", () => editUser(user));
        buttons[1].addEventListener("click", () => deleteUser(user["職員番号ログインID"] || ""));
        tbody.appendChild(tr);
    });
}

function editUser(user) {
    setValue("year", user["年度"]);
    setValue("groupCd", user["所属CD"]);
    setValue("groupName", user["所属名称"]);
    setValue("id", user["職員番号ログインID"]);
    setValue("sei", user["姓"]);
    setValue("mei", user["名"]);
    setValue("tantoCd", user["担当CD"]);
    setValue("tantoName", user["担当名称"]);
}

async function createUser() {
    const data = {
        adoOuDn: valueOf("adoOuDn"),
        groupCd: valueOf("groupCd"),
        id: valueOf("id"),
        sei: valueOf("sei"),
        mei: valueOf("mei"),
        tantoCd: valueOf("tantoCd"),
        year: valueOf("year"),
        datestart: valueOf("datestart"),
        kanri: checkedOf("kanri"),
        syoku: checkedOf("syoku")
    };

    if (!data.id) {
        alert("職員番号／ログインIDを入力してください");
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/api/users/create`, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(data)
        });
        const result = await response.json();
        if (!response.ok || result.result !== "ok") {
            alert(result.message || "新規追加に失敗しました");
            return;
        }
        alert("新規追加APIへ送信しました");
        await loadUsers();
    } catch (error) {
        console.error(error);
        alert("新規追加APIへの接続に失敗しました");
    }
}

async function deleteUser(id) {
    if (!id) return alert("削除対象の職員番号がありません");
    if (!confirm(`職員番号 ${id} を削除しますか？`)) return;

    try {
        const response = await fetch(`${API_BASE}/api/users/delete`, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({id})
        });
        const result = await response.json();
        if (!response.ok || result.result !== "ok") {
            alert(result.message || "削除に失敗しました");
            return;
        }
        alert("削除しました");
        await loadUsers();
    } catch (error) {
        console.error(error);
        alert("削除APIへの接続に失敗しました");
    }
}

async function loadAdOus() {
    const select = document.getElementById("adoOuDn");
    if (!select) return;

    try {
        const response = await fetch(`${API_BASE}/api/ad/ous`);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const ous = await response.json();
        select.innerHTML = '<option value="">-- AD配属先を選択 --</option>';
        ous.forEach(ou => {
            const option = document.createElement("option");
            option.value = ou.dn;
            option.textContent = ou.name;
            select.appendChild(option);
        });
    } catch (error) {
        console.error("AD OU取得失敗:", error);
    }
}

function valueOf(id) {
    const e = document.getElementById(id);
    return e ? e.value.trim() : "";
}
function checkedOf(id) {
    const e = document.getElementById(id);
    return e ? e.checked : false;
}
function setValue(id, value) {
    const e = document.getElementById(id);
    if (e) e.value = value == null ? "" : value;
}
function escapeHtml(value) {
    return String(value)
        .replace(/&/g, "&amp;").replace(/</g, "&lt;")
        .replace(/>/g, "&gt;").replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}
