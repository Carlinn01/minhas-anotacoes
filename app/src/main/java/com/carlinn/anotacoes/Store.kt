package com.carlinn.anotacoes

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

enum class Tipo { TAREFA, COMPRA, META }

data class Item(
    val id: Long = System.currentTimeMillis(),
    val tipo: Tipo,
    val titulo: String,
    val nota: String = "",
    val feito: Boolean = false,
    val prazo: Long? = null,
    val lembrete: Long? = null,
    val quantidade: String = "",
    val progresso: Int = 0,
    val criadoEm: Long = System.currentTimeMillis(),
    val feitoEm: Long? = null,
)

private fun Item.paraJson() = JSONObject().apply {
    put("id", id)
    put("tipo", tipo.name)
    put("titulo", titulo)
    put("nota", nota)
    put("feito", feito)
    prazo?.let { put("prazo", it) }
    lembrete?.let { put("lembrete", it) }
    put("quantidade", quantidade)
    put("progresso", progresso)
    put("criadoEm", criadoEm)
    feitoEm?.let { put("feitoEm", it) }
}

private fun JSONObject.longOuNulo(nome: String) = if (has(nome)) getLong(nome) else null

private fun itemDeJson(o: JSONObject) = Item(
    id = o.getLong("id"),
    tipo = Tipo.valueOf(o.getString("tipo")),
    titulo = o.getString("titulo"),
    nota = o.optString("nota"),
    feito = o.optBoolean("feito"),
    prazo = o.longOuNulo("prazo"),
    lembrete = o.longOuNulo("lembrete"),
    quantidade = o.optString("quantidade"),
    progresso = o.optInt("progresso"),
    criadoEm = o.optLong("criadoEm"),
    feitoEm = o.longOuNulo("feitoEm"),
)

/** Guarda tarefas, compras e metas no próprio celular (SharedPreferences em JSON). */
object Store {
    val itens = mutableStateListOf<Item>()
    private var prefs: SharedPreferences? = null
    private lateinit var app: Context

    fun init(context: Context) {
        if (prefs != null) return
        app = context.applicationContext
        val p = app.getSharedPreferences("dados", Context.MODE_PRIVATE)
        prefs = p
        val raw = p.getString("itens", null) ?: return
        val arr = JSONArray(raw)
        for (i in 0 until arr.length()) itens.add(itemDeJson(arr.getJSONObject(i)))
    }

    fun get(id: Long) = itens.firstOrNull { it.id == id }

    fun salvar(item: Item) {
        val i = itens.indexOfFirst { it.id == item.id }
        if (i >= 0) itens[i] = item else itens.add(item)
        persistir()
        Lembretes.agendar(app, item)
    }

    fun alternarFeito(id: Long) {
        val item = get(id) ?: return
        val feito = !item.feito
        salvar(
            item.copy(
                feito = feito,
                feitoEm = if (feito) System.currentTimeMillis() else null,
                progresso = if (feito && item.tipo == Tipo.META) 100 else item.progresso,
            )
        )
    }

    fun avancarMeta(id: Long) {
        val item = get(id) ?: return
        val novo = (item.progresso + 10).coerceAtMost(100)
        val concluiu = novo >= 100 && !item.feito
        salvar(
            item.copy(
                progresso = novo,
                feito = item.feito || concluiu,
                feitoEm = if (concluiu) System.currentTimeMillis() else item.feitoEm,
            )
        )
    }

    fun remover(id: Long) {
        val item = get(id) ?: return
        itens.remove(item)
        persistir()
        Lembretes.cancelar(app, id)
    }

    fun reagendarTodos() {
        itens.forEach { Lembretes.agendar(app, it) }
    }

    private fun persistir() {
        val arr = JSONArray()
        itens.forEach { arr.put(it.paraJson()) }
        prefs?.edit()?.putString("itens", arr.toString())?.apply()
    }
}
