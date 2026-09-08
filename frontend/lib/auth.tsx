'use client';

/**
 * Sessão do cliente.
 *
 * O token da API vive no localStorage. É uma escolha com custo conhecido: fica exposto a XSS.
 * A alternativa — cookie httpOnly — exigiria um BFF e reabriria o CSRF que o backend fechou
 * de propósito ao autenticar por header. Enquanto o front for SPA falando com a API por
 * `Authorization`, esta é a opção coerente.
 *
 * O token dura 1 hora e não existe refresh nem revogação no backend. Então a sessão acaba no
 * meio da navegação, sem aviso: todo 401 tem que virar "entre de novo", e o carrinho sobrevive
 * porque mora no Redis atrelado ao usuário, não aqui.
 */

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { ApiError, api } from './api';
import type { User } from './types';

const TOKEN_KEY = 'volt.token';

export type Role = 'USER' | 'OWNER';

/**
 * Lê o papel de dentro do próprio token, sem validar assinatura.
 *
 * Isso é decisão de INTERFACE, não de segurança: serve para saber qual chamada faz sentido
 * fazer a seguir. Quem barra de verdade é o backend, que valida o mesmo token de novo em
 * toda requisição — um token adulterado aqui só faria a tela pedir algo que a API recusa.
 *
 * Existe porque o `sub` de um token de OWNER é o id do **owner**, e não há linha em `users`
 * com esse id: chamar `/users/me` com token de dono responde 404, e era isso que derrubava
 * o login inteiro do dono.
 */
function papelDoToken(token: string): Role | null {
  try {
    const corpo = token.split('.')[1];
    if (!corpo) return null;
    const json = atob(corpo.replace(/-/g, '+').replace(/_/g, '/'));
    const papel = (JSON.parse(json) as { role?: string }).role;
    return papel === 'OWNER' || papel === 'USER' ? papel : null;
  } catch {
    return null;
  }
}

interface AuthState {
  token: string | null;
  /** Null quando quem está logado é o dono — não existe linha em `users` para ele. */
  user: User | null;
  role: Role | null;
  loading: boolean;
  /** Troca o ID token do Google por um token desta API. */
  signInWithGoogle: (idToken: string) => Promise<void>;
  signOut: () => void;
  /** Rechama /users/me — use depois de completar CPF/telefone. */
  refresh: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [token, setToken] = useState<string | null>(null);
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  const role = token ? papelDoToken(token) : null;

  const clear = useCallback(() => {
    setToken(null);
    setUser(null);
    try {
      localStorage.removeItem(TOKEN_KEY);
    } catch {
      /* nada a fazer */
    }
  }, []);

  /**
   * localStorage não existe no servidor, então a leitura só pode acontecer depois da montagem.
   *
   * O token só entra no estado DEPOIS de `/users/me` responder. Guardá-lo antes deixaria a
   * aplicação um instante com um token que ainda pode ser inválido — e é exatamente esse o
   * caso quando a sessão de uma hora expirou entre duas visitas.
   */
  useEffect(() => {
    let vivo = true;

    (async () => {
      let stored: string | null = null;
      try {
        stored = localStorage.getItem(TOKEN_KEY);
      } catch {
        // navegador com armazenamento bloqueado: segue deslogado, sem quebrar a página
      }
      if (!stored) {
        if (vivo) setLoading(false);
        return;
      }
      try {
        // Dono não tem perfil de cliente: o `sub` dele é id de owner. Pedir `/users/me`
        // aqui responderia 404 e derrubaria a sessão de quem acabou de entrar certo.
        const me = papelDoToken(stored) === 'OWNER'
          ? null
          : await api<User>('/users/me', { token: stored });
        if (!vivo) return;
        setToken(stored);
        setUser(me);
      } catch {
        if (vivo) clear();
      } finally {
        if (vivo) setLoading(false);
      }
    })();

    return () => {
      vivo = false;
    };
  }, [clear]);

  const signInWithGoogle = useCallback(async (idToken: string) => {
    const res = await api<{ accessToken: string }>('/auth/google', {
      method: 'POST',
      body: { idToken },
    });
    try {
      localStorage.setItem(TOKEN_KEY, res.accessToken);
    } catch {
      /* sessão só desta aba */
    }
    setToken(res.accessToken);
    setUser(papelDoToken(res.accessToken) === 'OWNER'
      ? null
      : await api<User>('/users/me', { token: res.accessToken }));
  }, []);

  const refresh = useCallback(async () => {
    if (!token || papelDoToken(token) === 'OWNER') return;
    try {
      setUser(await api<User>('/users/me', { token }));
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) clear();
    }
  }, [token, clear]);

  const value = useMemo<AuthState>(
    () => ({ token, user, role, loading, signInWithGoogle, signOut: clear, refresh }),
    [token, user, role, loading, signInWithGoogle, clear, refresh],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth precisa estar dentro de <AuthProvider>');
  return ctx;
}

/**
 * Chamada autenticada com tratamento de sessão expirada embutido.
 *
 * Devolve `signedOut` em vez de estourar quando o token morreu, para a tela poder pedir login
 * de novo em vez de mostrar erro genérico.
 */
export function useApi() {
  const { token, signOut } = useAuth();

  return useCallback(
    async <T,>(path: string, options: Parameters<typeof api>[1] = {}): Promise<T> => {
      try {
        return await api<T>(path, { ...options, token });
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) signOut();
        throw e;
      }
    },
    [token, signOut],
  );
}
