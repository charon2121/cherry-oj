package runner

import (
	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/sandbox/internal/container"
	"cherry-oj/judge-engine/sandbox/internal/store"
	"errors"
	"fmt"
	"io"
)

func collect(c container.Container, st store.Store, spec contract.RunSpec, result contract.RunResult) contract.RunResult {
	result.Outputs = map[string]string{}
	result.Artifacts = map[string]string{}
	err := readInlineOutputs(c, spec.Outputs, result.Outputs)
	if err != nil {
		result.Status = contract.StatusWorkspaceError
	} else {
		result.Status, err = storeArtifacts(c, st, spec.Artifacts, result.Artifacts)
	}
	if err == nil {
		return result
	}

	// 全部产物成功才发布 ref；保留原始错误，并报告每个回滚失败。
	result.Error = err.Error()
	for _, ref := range result.Artifacts {
		if err := st.Delete(ref); err != nil {
			result.Error += "; 回滚产物: " + err.Error()
		}
	}
	result.Outputs, result.Artifacts = nil, nil
	return result
}

func readInlineOutputs(c container.Container, names []string, outputs map[string]string) error {
	remaining := MaxInlineBytes
	for _, name := range names {
		rc, err := c.GetFile(name)
		if err != nil {
			return err
		}
		data, err := io.ReadAll(io.LimitReader(rc, remaining+1))
		err = errors.Join(err, rc.Close())
		if int64(len(data)) > remaining {
			err = errors.Join(err, fmt.Errorf("内联产物总量超过%d bytes", MaxInlineBytes))
		}
		if err != nil {
			return err
		}
		remaining -= int64(len(data))
		outputs[name] = string(data)
	}
	return nil
}

func storeArtifacts(c container.Container, st store.Store, names []string, artifacts map[string]string) (contract.Status, error) {
	for _, name := range names {
		if _, ok := artifacts[name]; ok {
			continue
		}
		rc, err := c.GetFile(name)
		if err != nil {
			return contract.StatusWorkspaceError, err
		}
		ref, err := st.Put(rc)
		// Put 成功后即登记，随后 Close 失败也可以回滚。
		if err == nil {
			artifacts[name] = ref
		}
		err = errors.Join(err, rc.Close())
		if err != nil {
			return contract.StatusInternalError, err
		}
	}
	return contract.StatusOK, nil
}
